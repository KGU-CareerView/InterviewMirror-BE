# AI 서버 알림 — 프론트 `/app/realtime.audio` 페이로드 변경 (백엔드 hotfix 반영 완료)

## TL;DR

**AI 서버 측 변경 작업은 필요 없습니다.**
프론트와 백엔드 간 WebSocket DTO만 변경되었으며, AI 서버로 전송되는 gRPC 메시지(`VoiceToneAnalysisRequest`, `FinalReportRequest`, `FeatureRequest` 등)의 스키마/내용은 동일합니다. 정보 공유와 사전 확인을 위해 문서를 공유합니다.

---

## 1. 배경

프론트엔드의 AudioWorklet은 약 1초 단위(window) 음성 특징을 생성합니다. 기존에는 1초 인터벌마다 **최신 window 1개만** WebSocket으로 백엔드에 전송했는데, worklet 주기와 전송 주기의 어긋남으로 일부 window가 유실되었습니다.

이를 해결하기 위해 프론트는 **직전 전송 이후 누적된 모든 window를 `windows: AudioWindow[]` 배열로 묶어 한 번에 전송**하도록 변경되었습니다. 백엔드는 이 배열을 받아 집계 후 기존 단일 features 처리 로직에 전달합니다.

상세 스펙은 `docs/HOTFIX.md` 참고.

---

## 2. AI 서버와의 gRPC 인터페이스 — 변경 없음

### 2.1 `FeatureRequest` (FaceAnalysisStream)
- **변경 없음.** 영상 프레임 기반 얼굴 분석으로, 오디오 페이로드 변경과 무관합니다.

### 2.2 `VoiceToneAnalysisRequest` (AnalyzeVoiceTone)
- **변경 없음.** 백엔드가 보내는 필드는 다음 그대로입니다:
  - `audio_summary` (`AudioSummaryData`)
  - `zcr_samples` (`repeated float`)
  - `response_time_seconds`, `session_id`, `user_id`, `question_index`
- `audio_summary`는 프론트가 답변 종료 시점에 별도로 계산해 `/app/session.answer`로 보내는 값으로, 이번 hotfix 범위 밖입니다.
- `zcr_samples`는 백엔드가 Redis에 1초 단위로 누적해온 시계열입니다. 단위/의미 변동 없음.

### 2.3 `FinalReportRequest` (GenerateFinalReport)
- **변경 없음.** 질문/답변/감정/음성 요약 등 모든 필드 동일.

### 2.4 `InitialQuestionGenerateRequest` / `FollowUpQuestionGenerateRequest`
- **변경 없음.** 질문 생성과는 무관.

---

## 3. 백엔드 내부 처리 흐름 (참고용)

```
프론트 ──── /app/realtime.audio { windows: [...] } ────▶ 백엔드 WebSocket 핸들러
                                                              │
                                                              ▼
                                                  windows 집계 (HOTFIX.md 규칙):
                                                    rms              → 평균
                                                    zeroCrossingRate → 평균
                                                    isSpeaking       → OR
                                                    speechDurationMs → 합산
                                                    silenceDurationMs→ 합산
                                                    peakAmplitude    → 최댓값
                                                              │
                                                              ▼
                                                기존 단일 features 처리 로직
                                                  - silence window 카운트 / 피드백 발행
                                                  - ZCR 시계열을 Redis에 push (집계된 평균 1개)
                                                              │
                                                              ▼
                                            (질문 종료 시점) FinalReportService
                                                              │
                                                              ▼ gRPC
                                                       ┌──────────────┐
                                                       │   AI 서버    │
                                                       └──────────────┘
```

AI 서버에 도달하는 데이터의 **스키마/단위/의미는 변경 전과 동일**합니다.

---

## 4. AI 서버 측이 확인해주실 사항 (요청 사항)

다음 항목에 대해 회신 부탁드립니다.

### 4.1 ZCR 샘플 시계열 해상도 확인
백엔드는 windows 배열을 **하나의 평균값으로 집계**한 뒤 Redis에 1샘플로 push합니다.
- **변경 전**: 1초당 1샘플 (worklet 주기와 일치할 때)
- **변경 후**: 1초당 1샘플 (windows 평균을 1샘플로 push) — **이름값상 동일**

다만 누적된 windows가 2개 이상인 경우(예: 프론트 전송이 잠시 지연되어 2초치가 한 번에 옴):
- 기존: 1초치 1샘플만 받고 다른 1초치는 유실
- 변경 후: 2초치 평균 1샘플 1개

→ **`VoiceToneAnalysisRequest.zcr_samples`의 샘플 수와 1샘플이 가리키는 시간 간격에 의존하는 분석이 있는지** 확인 부탁드립니다. 통상 평균이나 분산 통계만 사용한다면 영향 없음.

### 4.2 회신 불요한 항목
- gRPC 스키마 변경: **불필요**
- AI 서버 코드 수정: **불필요**
- 재배포: **불필요**

---

## 5. 백엔드 변경 파일 (참고)

| 파일 | 변경 내용 |
|------|----------|
| `src/main/java/com/interviewmirror/realtime/dto/RealtimeAudioRequest.java` | `features` 단일 → `windows: List<RealtimeAudioFeatures>` |
| `src/main/java/com/interviewmirror/realtime/dto/RealtimeAudioFeatures.java` | `@Builder`/`@AllArgsConstructor` 추가 (집계 결과 생성용) |
| `src/main/java/com/interviewmirror/realtime/service/RealtimeService.java` | `analyzeAudio()`에 `aggregateWindows()` 적용 |
| `src/main/java/com/interviewmirror/realtime/controller/RealtimeWebSocketController.java` | 로그 메시지를 `windowCount` 출력으로 변경 |

배포 시점: 백엔드 hotfix 머지 즉시 적용.

---

## 6. 문의

질문이나 추가 영향 있는 부분 발견 시 백엔드 채널로 회신 부탁드립니다.
