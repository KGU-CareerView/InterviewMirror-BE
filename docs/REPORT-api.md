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

| HTTP 상태 | 에러 코드 | 케이스 | 프론트 처리 |
|-----------|----------|--------|------------|
| 404 | `SESSION_NOT_FOUND` | 세션이 존재하지 않음 | 에러 표시 |
| 401 | `AUTH_UNAUTHORIZED` | 다른 유저의 세션 조회 시도 | 에러 표시 |
| 404 | `REPORT_NOT_READY` | 세션은 있으나 AI 분석이 아직 진행 중 | **폴링 또는 대기** (수 초~수십 초 후 재요청) |
| 502 | `REPORT_GENERATION_FAILED` | AI 서버 장애로 리포트 생성 영구 실패 | **재시도 버튼 노출** → `POST /report/retry` 호출 |

### 응답 본문 형식 (오류 공통)

```json
{
  "success": false,
  "error": {
    "code": "REPORT_GENERATION_FAILED",
    "message": "AI 서버 장애로 리포트 생성에 실패했습니다. 재시도 API를 호출해주세요."
  }
}
```

### 상태별 흐름

```
면접 종료 (ENDED) ──▶ AI 리포트 생성 요청 (백엔드 → AI 서버)
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
   ✅ 성공             ⏳ 일시 장애       ❌ 영구 실패
   COMPLETED         PENDING 유지        FAILED
        │                 │                 │
   GET /report         GET /report       GET /report
   → 200 OK            → 404             → 502
                       REPORT_NOT_READY  REPORT_GENERATION_FAILED
                       (계속 폴링)        ▼
                                       프론트가 사용자에게
                                       "재시도" 버튼 노출
                                       ▼
                                   POST /report/retry
                                       ▼
                                   다시 PENDING으로 전환
                                   → AI 재호출
```

**일시 장애 (PENDING 유지)** 로 판정하는 케이스:
- gRPC `RESOURCE_EXHAUSTED` (Gemini quota 등 rate limit)
- gRPC `UNAVAILABLE` (AI 서버 일시 다운)
- gRPC `DEADLINE_EXCEEDED` (응답 시간 초과)

위 케이스는 백엔드가 `PENDING` 상태를 유지하므로 프론트는 `REPORT_NOT_READY` 응답을 받습니다. 일정 시간 폴링 후 사용자가 직접 재시도를 트리거하고 싶을 때 `/report/retry`를 호출할 수도 있습니다.

**영구 실패 (FAILED)** 로 판정하는 케이스:
- 그 외 모든 에러 (예: `INTERNAL`, `INVALID_ARGUMENT` 등 백엔드/AI 코드 오류로 추정)

이 경우 즉시 `REPORT_GENERATION_FAILED`를 반환하여 무한 폴링을 방지합니다.

---

## 3. 리포트 재시도 — `POST /v1/interviews/{sessionId}/report/retry` (신규)

AI 서버 장애로 리포트 생성이 실패했을 때 (`REPORT_GENERATION_FAILED` 응답을 받은 경우), 또는 오랫동안 `REPORT_NOT_READY` 상태가 지속될 때 호출합니다.

### 요청

```
POST /v1/interviews/{sessionId}/report/retry
Authorization: Bearer <accessToken>
```

| 항목 | 값 |
|------|-----|
| 인증 | 필요 (Bearer 토큰) |
| Path Variable | `sessionId` — 재시도할 세션 ID |
| Request Body | 없음 |

### 성공 응답

```http
HTTP/1.1 202 Accepted
```

```json
{
  "success": true,
  "data": null
}
```

요청이 접수되었고 AI 서버에 비동기로 재요청을 시작했음을 의미합니다. **응답을 받은 후 다시 `GET /report`를 폴링**하여 결과를 확인하세요.

### 오류 응답

| HTTP 상태 | 에러 코드 | 케이스 |
|-----------|----------|--------|
| 404 | `SESSION_NOT_FOUND` | 세션이 존재하지 않음 |
| 401 | `AUTH_UNAUTHORIZED` | 다른 유저의 세션 |
| 400 | `INVALID_REQUEST` | **이미 리포트 생성이 완료된 세션** (재시도 불가) |

이미 `COMPLETED`된 리포트는 재시도할 수 없습니다. 기존 리포트를 그대로 사용하세요.

### 프론트 구현 예시

```typescript
async function fetchReport(sessionId: number) {
  try {
    const res = await api.get(`/v1/interviews/${sessionId}/report`);
    return { status: 'ok', data: res.data.data };
  } catch (err) {
    const code = err.response?.data?.error?.code;
    if (code === 'REPORT_NOT_READY') {
      return { status: 'pending' };       // 폴링 계속
    }
    if (code === 'REPORT_GENERATION_FAILED') {
      return { status: 'failed' };        // 재시도 버튼 노출
    }
    throw err;
  }
}

async function retryReport(sessionId: number) {
  await api.post(`/v1/interviews/${sessionId}/report/retry`);
  // 202 응답 후 폴링 재개
}
```

### 폴링 권장사항

- `REPORT_NOT_READY` 응답 시: 3~5초 간격으로 재시도, 총 60초까지 시도
- 60초 이상 `PENDING` 지속 시 사용자에게 "오래 걸리고 있습니다. 다시 시도하시겠습니까?" 안내 후 `/report/retry` 옵션 제공
- `REPORT_GENERATION_FAILED` 응답 시: 즉시 재시도 UI 노출, 자동 재시도는 하지 않음 (사용자 액션 필요)

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
