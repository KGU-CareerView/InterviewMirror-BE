# 최종 리포트 API 변경 사항 (프론트엔드 전달용)

## 변경 요약

WebSocket 메시지는 기존 VOICE.md 명세와 동일합니다. **변경 사항은 두 가지 REST API의 응답 구조**입니다.

1. `GET /v1/interviews/{sessionId}/report` — 종합 리포트 (기존)
2. `GET /v1/interviews/{sessionId}/result` — 질문별 결과 (질문 점수/피드백 추가)

---

## 변경 없음 — WebSocket 메시지

`/app/realtime.audio`, `/app/session.answer`, `/app/realtime.end` 메시지 구조는 VOICE.md 명세와 동일합니다. 프론트엔드에서 수정할 내용은 없습니다.

---

## 1. 종합 리포트 — `GET /v1/interviews/{sessionId}/report`

### 응답 구조

```json
{
  "success": true,
  "data": {
    "sessionId": 123,
    "videoUrl": "https://s3.../video.mp4",
    "emotionGraphJson": "[...]",
    "totalScore": 82,
    "feedback": "전반적으로 논리적인 답변 구성을 보여주셨습니다...",
    "strengths": "[{\"title\":\"논리적 구성\",\"detail\":\"각 답변의 시작과 끝이 명확했습니다.\"}]",
    "weaknesses": "[{\"title\":\"발화 속도\",\"detail\":\"...\",\"improvement\":\"...\"}]",
    "aiAnalysisJson": "{ ... }"
  }
}
```

### `strengths` / `weaknesses` — JSON 배열 문자열

`JSON.parse()` 후 사용합니다.

```typescript
interface Strength { title: string; detail: string; }
interface Weakness { title: string; detail: string; improvement: string; }

const strengths: Strength[] = JSON.parse(report.strengths ?? "[]");
const weaknesses: Weakness[] = JSON.parse(report.weaknesses ?? "[]");
```

### `aiAnalysisJson` — 전체 AI 응답 (확장됨)

AI 서버의 `FinalReportResponse` 전체를 JSON 문자열로 보관합니다. 이제 **질문별 피드백 배열(`questionFeedbacks`)이 포함**됩니다.

```typescript
interface AiAnalysis {
  sessionId: string;
  userId: string;
  overallSummary: string;       // 전체 요약 문장
  overallScore: number;         // 종합 점수 (0~100)
  contentScore: number;         // 답변 내용 점수
  voiceScore: number;           // 음성 점수
  expressionScore: number;      // 표정/감정 점수
  strengths: Strength[];
  weaknesses: Weakness[];
  timeBasedInsights: TimeInsight[];
  finalAdvice: string;
  questionFeedbacks: QuestionFeedback[];  // ★ 신규: 질문별 피드백
  audio?: { overallScore: number; scoredQuestionCount: number; };
}

interface TimeInsight {
  timeRange: string;
  observation: string;
  suggestion: string;
}

interface QuestionFeedback {
  index: number;
  totalScore: number;
  contentScore: number;
  voiceScore: number;
  expressionScore: number;
  overallFeedback: string;
  contentFeedback: string;
  voiceFeedback: string;
  expressionFeedback: string;
}

const aiAnalysis: AiAnalysis = JSON.parse(report.aiAnalysisJson ?? "{}");
```

---

## 2. 질문별 결과 — `GET /v1/interviews/{sessionId}/result` (변경됨)

### 응답 구조

각 질문 항목에 점수와 피드백 필드가 추가되었습니다.

```json
{
  "success": true,
  "data": {
    "sessionId": 123,
    "videoUrl": "https://s3.../video.mp4",
    "createTime": "2026-05-25T10:30:00",
    "emotionGraph": "{...}",
    "details": [
      {
        "qId": 1,
        "question": "본인의 강점을 말씀해주세요.",
        "answer": "저는 백엔드 개발 경험이 3년 있으며 ...",
        "emotionResult": "{...}",
        "responseTimeSeconds": 87,
        "totalScore": 85,
        "contentScore": 88,
        "voiceScore": 82,
        "expressionScore": 90,
        "feedback": "답변 구성이 명확하고 사례 제시가 좋았습니다.",
        "contentFeedback": "구체적인 경험을 잘 전달하셨습니다.",
        "voiceFeedback": "발화 속도가 적절했습니다.",
        "expressionFeedback": "전반적으로 안정적인 표정을 유지했습니다."
      }
    ]
  }
}
```

### 질문별 필드 설명

| 필드 | 설명 | 비고 |
|------|------|------|
| `qId` | 질문 ID | |
| `question` | 질문 내용 | |
| `answer` | **STT 변환된 답변 (대본)** | 면접자가 말한 내용을 그대로 보관 |
| `emotionResult` | 질문별 표정 감정 분석 (JSON 문자열) | |
| `responseTimeSeconds` | 답변 소요 시간 (초) | |
| `totalScore` | 질문별 종합 점수 (0~100) | AI 산출 |
| `contentScore` | 답변 내용/정확도 점수 | AI 산출 |
| `voiceScore` | 목소리 점수 | AI 산출 (`AnalyzeVoiceTone` 결과) |
| `expressionScore` | 표정 점수 | AI 산출 |
| `feedback` | 질문별 종합 피드백 | AI 산출 |
| `contentFeedback` | 답변 내용 피드백 | AI 산출 |
| `voiceFeedback` | 목소리 피드백 | AI 산출 |
| `expressionFeedback` | 표정 피드백 | AI 산출 |

> **주의**: AI 분석 완료 전(`/result` 호출 시점이 분석 완료보다 빠를 때)에는 점수/피드백 필드가 `null`일 수 있습니다. 분석 완료 여부는 `/report` 엔드포인트로 판단하세요.

---

## 오류 응답

| HTTP 상태 | 케이스 |
|-----------|--------|
| 404 | 세션이 존재하지 않음 |
| 403 | 다른 유저의 세션 조회 시도 |
| 202 | 세션은 있으나 AI 분석이 아직 완료되지 않음 (`REPORT_NOT_READY`) — `/report`에서만 발생 |

AI 분석은 면접 종료 후 비동기로 생성됩니다. `/report`가 202를 반환하면 폴링 또는 완료 알림 후 재요청하세요.

---

## 전체 데이터 흐름

```
[면접 중]
AudioWorklet → /app/realtime.audio (1초마다, zeroCrossingRate 포함)
답변 완료   → /app/session.answer (audioSummary 포함, answer=STT 텍스트)
면접 종료   → /app/realtime.end (includesAudio: true)

[면접 종료 후 — 비동기]
백엔드:
  1. 질문별 AI VoiceTone 분석 (병렬 gRPC) → InterviewDetail.audioScore 업데이트
  2. 모든 정보 종합하여 AI GenerateFinalReport 요청
     - 전달: 카테고리, 자소서, 질문별(STT 대본, 표정 결과, 음성 데이터, ZCR, 응답시간),
            세션 전체 감정 타임라인
  3. AI 응답 수신 → 질문별 점수/피드백 InterviewDetail에 반영
  4. 종합 점수/피드백 InterviewReport에 저장

[프론트 조회]
GET /v1/interviews/{sessionId}/report
  → 전체 종합 점수 + aiAnalysisJson.questionFeedbacks

GET /v1/interviews/{sessionId}/result
  → 질문별 STT 대본 + 점수 + 피드백 (DB에서 직접 조회 가능)
```

## 최종 점수 산출 근거 (AI 서버 처리)

AI 서버는 백엔드에서 받은 아래 정보를 종합하여 최종 점수를 산출합니다.

1. **표정 인식 감정 분석** — 질문별 `emotionResult` + 세션 `emotionGraphJson` 타임라인
2. **답변 정확도/내용** — STT 변환된 `answer` 텍스트 + `category`/`resumeText` 컨텍스트
3. **답변 길이/응답 시간** — `answerLength` + `responseTimeSeconds`
4. **목소리 분석** — `audioSummary` (rms, wpm, pauseCount, ttr 등) + `zcrSamples` 시계열 + 이미 계산된 `voiceScore`
