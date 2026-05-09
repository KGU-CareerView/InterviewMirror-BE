# InterviewMirror 기술 명세서

## 1. 목적
PRD/기능 명세를 구현하기 위한 아키텍처, 데이터, 성능, 안정성 기준을 정의한다.

## 2. 시스템 아키텍처
## 2.1 컴포넌트
- API Gateway: 인증, 라우팅, 레이트리밋
- Interview Orchestrator: 세션 상태 머신 관리
- RAG Service: 문서 인덱싱/검색/질문 생성
- Multimodal Analyzer: 영상/음성 특징 추출
- Semantic Evaluator: 내용 평가 및 XAI 생성
- Realtime Coach Engine: 저지연 코칭 생성
- Analytics Service: 장기 지표 집계/조회
- Storage: MySQL(정형), Redis(실시간), Object Storage(원본 미디어)

## 2.2 통신 패턴
- 제어면: REST API
- 실시간면: WebSocket(코칭, 상태 이벤트)
- 비동기 처리: 메시지 큐(분석/집계 잡)

## 3. 핵심 시퀀스
## 3.1 세션 시작
1) 세션 생성  
2) 환경 캘리브레이션 수행  
3) 문서 업로드/인덱싱  
4) 질문 트리 생성  
5) 실시간 면접 시작

## 3.2 실시간 코칭 파이프라인
`Audio Capture -> VAD/Chunk -> STT -> Intent/State -> Coach LLM -> Prompt Renderer`

목표: E2E P95 < 1.5초

## 3.3 세션 종료 후
트랜스크립트 정제 -> 의미론 평가 -> 멀티모달 요약 -> 리포트 생성 -> 장기 집계 반영

## 4. 데이터 모델 (초안)
## 4.1 주요 테이블
- `users`
- `interview_sessions` (status, started_at, ended_at, latency_stats)
- `session_documents` (type: resume/jd, parsed_text, embedding_ref)
- `question_items` (seed, followup, rationale_ref)
- `session_events` (timestamp, event_type, payload)
- `semantic_scores` (consistency, structure, specificity, explanation)
- `multimodal_scores` (eye_contact, pacing, filler_rate, tone_confidence)
- `coaching_prompts` (prompt_type, content, delivered_at)
- `analytics_snapshots` (period, metrics_json)

## 4.2 인덱스/파티셔닝
- 세션 이벤트: `session_id + timestamp` 복합 인덱스
- 대용량 이벤트/미디어 메타는 기간 파티셔닝

## 5. RAG 설계
## 5.1 인제스트
- 자소서/JD 텍스트 정규화
- 의미 단위 청킹 + 임베딩 생성

## 5.2 검색
- 하이브리드 검색(BM25 + Vector)
- 재정렬(re-ranking)로 질문 근거 후보 선택

## 5.3 생성
- 질문 템플릿 + 컨텍스트 주입
- 출력 스키마 강제(JSON schema)
- 질문마다 근거 텍스트 참조 포함

## 6. 의미론 평가 설계
- 루브릭: 일관성, 논리구조, 구체성, 문제해결 명료성
- 평가 출력:
  - 항목별 점수
  - 가감점 근거
  - 개선 액션
- XAI:
  - "근거 문장"과 "판단 규칙"을 함께 저장
  - 리포트에서 근거 추적 가능

## 7. 멀티모달 분석 설계
- Video Feature: gaze, expression stability, head pose
- Audio Feature: WPM, pause, filler ratio, pitch variance
- Feature Calibration:
  - 조명/노이즈/각도 보정
  - 신뢰도 낮은 구간은 점수 반영 가중치 하향

## 8. 저지연/성능 설계
## 8.1 지연 예산(권장)
- 캡처+전송: 200ms
- STT: 500ms
- 코칭 추론: 500ms
- 렌더링/전달: 300ms

## 8.2 최적화 전략
- Zero-copy 오디오 버퍼 전달
- 스트리밍 STT + 부분 결과 활용
- 경량 모델 우선, 고급 평가는 비동기 후처리
- Redis로 세션 상태 캐시

## 9. 무제한 세션 안정성 설계
- 세션 상태 스냅샷 주기 저장
- heartbeat timeout + 재연결 토큰
- 노드 장애 시 상태 복원 및 스트림 재구독
- 장시간 실행 대비 메모리 가드레일/백프레셔

## 10. 보안/개인정보
- 전송 구간 TLS
- 저장 구간 민감정보 암호화
- 원본 오디오/영상 보관 기간 정책
- 접근 로그/감사 추적

## 11. 관측성/운영
## 11.1 필수 메트릭
- `realtime_latency_p95/p99`
- `session_recovery_success_rate`
- `semantic_eval_failure_rate`
- `multimodal_signal_quality`
- `prompt_delivery_delay`

## 11.2 알림
- 지연 임계치 초과
- 세션 복구 실패
- 분석 파이프라인 적체

## 12. 테스트 전략
- Unit: 파서, 스코어러, 룰엔진
- Integration: 세션 전주기
- Load: 동시 세션 부하
- Soak: 90분+ 장시간 안정성
- Quality: XAI 근거 적합성 샘플 검증

## 13. 배포 전략
- 단계적 릴리즈(canary)
- 기능 플래그로 실시간 코칭/평가 모델 전환
- 롤백: 버전별 모델/파이프라인 스위치 지원

## 14. 오픈 이슈
- 기업 동향 데이터 소스 표준화 방식
- 평가 편향 최소화를 위한 휴먼 검수 비율
- 모델 비용 최적화 기준선
