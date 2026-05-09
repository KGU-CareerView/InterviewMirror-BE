# gRPC Feedback 도메인 아키텍처 설계

## 1. emotion_analysis.proto 구조

### 서비스 정의

| RPC | 방식 | 용도 |
|-----|------|------|
| `Analyze` | Unary | 단일 프레임 분석 |
| `AnalyzeStream` | Bidirectional Streaming | 실시간 영상 프레임 연속 분석 |

### 요청 메시지 — `AnalyzeRequest`

| 필드 | 타입 | 설명 |
|------|------|------|
| `session_id` | string | 면접 세션 식별자 |
| `timestamp` | int64 | 프레임 타임스탬프 (ms) |
| `cropped_face` | bytes | 얼굴 영역 크롭 이미지 바이너리 |
| `face_detected` | bool | 얼굴 감지 여부 |
| `landmarks` | FaceLandmarks | 시선 추적용 랜드마크 좌표 |

### 응답 메시지 — `AnalyzeResponse`

| 필드 | 타입 | 설명 |
|------|------|------|
| `session_id` | string | 세션 ID |
| `timestamp` | int64 | 분석된 프레임 타임스탬프 |
| `prediction` | string | 감정 예측 결과: `Stable` / `Nervous` / `Neutral` |
| `class_id` | int32 | 예측 클래스 숫자 ID |
| `confidence` | float | 최고 클래스 확신도 (0.0~1.0) |
| `stable_prob` | float | Stable 클래스 확률 |
| `nervous_prob` | float | Nervous 클래스 확률 |
| `neutral_prob` | float | Neutral 클래스 확률 |

---

## 2. .proto 파일 위치 주의사항

`com.google.protobuf` Gradle 플러그인은 `.proto` 파일을 `src/main/proto/`에서만 인식합니다.

```
src/main/proto/emotion_analysis.proto    ← 올바른 위치
src/main/resources/emotion_analysis.proto ← 인식 안 됨 (이전 위치)
```

빌드 후 생성되는 파일 위치:

```
build/generated/source/proto/main/
├── grpc/com/interviewmirror/grpc/proto/
│   └── EmotionAnalysisGrpc.java
└── java/com/interviewmirror/grpc/proto/
    ├── AnalyzeRequest.java
    ├── AnalyzeResponse.java
    ├── FaceLandmarks.java
    └── FeedbackProto.java
```

---

## 3. Feedback 도메인 구현 순서

### 1단계 — gRPC Client 컴포넌트

생성된 `EmotionAnalysisGrpc` stub을 래핑하는 클라이언트 클래스

```
feedback/client/EmotionAnalysisClient.java
```

- `@GrpcClient("ai-server")` 어노테이션으로 stub 주입
- `application.yml`의 `grpc.client.ai-server` 설정과 연결

### 2단계 — Feedback 도메인 구조

```
feedback/
├── client/EmotionAnalysisClient.java
├── dto/FeedbackRequest.java
├── dto/FeedbackResponse.java
├── entity/FeedbackResult.java       (면접 종료 후 집계 저장용)
├── repository/FeedbackRepository.java
├── service/FeedbackService.java
└── controller/FeedbackController.java
```

### 3단계 — FeedbackService 구현

`EmotionAnalysisClient` 주입 → gRPC 스트리밍 호출 → 결과 로컬 버퍼 적재 → Redis 배치 저장

### 4단계 — FeedbackController 구현

REST 엔드포인트 완성

---

## 4. 데이터 저장 전략

### DB 직접 저장 방식의 문제

스트리밍 환경에서 프레임마다 DB write 발생:
- 네트워크 I/O + 디스크 I/O + 트랜잭션 오버헤드
- 30fps 기준 초당 30번 DB write → 실질적으로 불가능

### Redis 버퍼링 방식 (채택)

```
스트리밍 중:
  AI 응답 → ConcurrentLinkedQueue (로컬 버퍼, 최대 크기 제한)
           → 스케줄러 (1~3초 주기) → Redis RPUSH (session_id 키)

면접 종료 시:
  Redis LRANGE → 집계 (평균 confidence, 감정 분포 등) → DB 1회 INSERT → Redis DEL
```

### DB 직접 저장 vs Redis 버퍼링 비교

| 항목 | DB 직접 저장 | Redis 버퍼링 |
|------|-------------|-------------|
| Write 지연 | 수~수십 ms | 1ms 미만 |
| Write 횟수 | 프레임마다 | 1~3초마다 배치 1회 |
| 디스크 I/O | 매번 발생 | 없음 (인메모리) |
| 부하 | 매우 높음 | 낮음 |

### 주의사항

- **로컬 버퍼 OOM 방지**: `ConcurrentLinkedQueue` 최대 크기 제한 필요
- **Redis TTL 설정**: 면접 종료 후 미처리 데이터 소멸 방지를 위한 TTL 설정
- **면접 종료 시 집계**: Redis 데이터를 DB에 1회 집계 저장 후 Redis 키 삭제

---

## 5. AI 서버 용량 계획

### 결론: 사용자마다 서버 1개 → 비효율

gRPC는 HTTP/2 멀티플렉싱 기반으로 하나의 서버가 수십~수백 개 스트림을 동시 처리합니다.
병목은 gRPC 연결이 아니라 **AI 모델 추론(inference)** 입니다.

### 용량 계산 방법

핵심 변수:

| 변수 | 예시 | 확인 방법 |
|------|------|----------|
| 추론 1회 소요시간 | 50ms | AI 서버에서 직접 측정 |
| 프레임 전송 주기 | 1fps | 설계 결정 |
| GPU 초당 처리량 | 40fps | 부하 테스트 |

```
동시 처리 가능 세션 수 = GPU 초당 처리량 / 세션당 초당 프레임 수
예시: 40fps ÷ 1fps = 40명 동시 처리 가능
```

### 부하 테스트 절차

1. 단일 추론 지연시간 측정 (AI 서버에서 직접)
2. `ghz` (gRPC 전용) 또는 `k6`로 동시 세션 수 증가시키며 테스트
3. p95 latency 임계점 확인

```
동시 10명 → p95: 60ms  ← OK
동시 30명 → p95: 80ms  ← OK
동시 50명 → p95: 300ms ← 한계 도달 → 서버당 최대 30~40명으로 결정
```

### 권장 확장 아키텍처

```
[사용자들]
    ↓
[Load Balancer]
    ↓           ↓           ↓
[AI 서버 1] [AI 서버 2] [AI 서버 3]  ← 부하에 따라 수평 확장 (HPA)
```

- 서버당 처리 한계 도달 시 새 인스턴스 자동 추가
- 세션 라우팅: `session_id` 기반 sticky session 또는 stateless 설계
- **현재 단계**: 서버 1대 기준으로 구현 → 부하 테스트 후 스케일 전략 결정
