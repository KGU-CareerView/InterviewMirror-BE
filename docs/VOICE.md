## 음성 분석 WebSocket API

기존 `/api/ws-interview` 연결(STOMP over SockJS)을 그대로 사용합니다. 별도 WebSocket 연결은 필요하지 않습니다.

### 설계 원칙

- raw PCM/오디오 파일은 전송하지 않습니다. 프론트엔드 AudioWorklet에서 집계한 특징값(feature)만 전송합니다.
- STT는 Web Speech API(브라우저 네이티브)로 프론트에서 처리합니다. 결과 텍스트는 답변 제출 시점에 한 번만 전송합니다.
- 실시간 전사(transcript) 스트리밍은 하지 않습니다. 다음 질문 생성 트리거가 답변 제출 버튼이므로, 최종 텍스트만 제출 페이로드에 포함하면 충분합니다.

### 전체 흐름

```
[답변 중]
AudioWorklet ──(1초 주기)──> /app/realtime.audio ──> 백엔드 실시간 집계
Web Speech API ──────────> 프론트 변수 누적 (transcript 문자열 append)

백엔드 ──> /topic/realtime/{sessionId}/audio ──> 프론트 실시간 피드백

[버튼 클릭]
/app/session.answer (answer=누적 STT 텍스트, audioSummary=집계값) ──> 다음 질문 생성

[면접 종료]
/app/realtime.end (includesAudio=true) ──> 백엔드 음성 최종 리포트 산출
```

### 추가 구독 토픽

기존 구독 목록에 아래를 추가합니다.

```
/topic/realtime/{sessionId}/audio
```

---

## SEND: 실시간 음성 특징 스트리밍

전송:

```
/app/realtime.audio
```

AudioWorklet이 1초 단위로 집계한 특징값을 전송합니다. 답변 버튼을 누르는 순간까지 지속 전송합니다.

페이로드:

```json
{
  "sessionId": "123",
  "userId": "1",
  "timestamp": 1716000000000,
  "questionIndex": 2,
  "windowMs": 1000,
  "features": {
    "rms": 0.034,
    "zeroCrossingRate": 0.12,
    "isSpeaking": true,
    "speechDurationMs": 740,
    "silenceDurationMs": 260,
    "peakAmplitude": 0.71
  }
}
```

필드 설명:

| 필드 | 타입 | 설명 | 계산 주체 |
|------|------|------|----------|
| `questionIndex` | number | 현재 진행 중인 질문 번호 (1-based) | 프론트 |
| `windowMs` | number | 집계 창 크기 (고정 1000) | 프론트 |
| `rms` | float | 창 내 RMS 평균 (음량 에너지) | AudioWorklet |
| `zeroCrossingRate` | float | 창 내 ZCR 평균 (고주파/노이즈 비율) | AudioWorklet |
| `isSpeaking` | boolean | VAD 판정 (rms 임계값 기반) | AudioWorklet |
| `speechDurationMs` | number | 창 내 발화 구간 합산 | AudioWorklet |
| `silenceDurationMs` | number | 창 내 침묵 구간 합산 | AudioWorklet |
| `peakAmplitude` | float | 창 내 최대 진폭 | AudioWorklet |

---

## SEND: 답변 제출 (기존 확장)

전송:

```
/app/session.answer
```

기존 페이로드에 `audioSummary` 필드를 추가합니다. `answer` 필드는 Web Speech API가 누적한 최종 전사 텍스트로 채웁니다.

페이로드:

```json
{
  "sessionId": 123,
  "answer": "저는 백엔드 개발 경험이 3년 있으며 Java와 Spring을 주로 사용했습니다.",
  "emotionResult": "STABLE",
  "responseTimeSeconds": 87,
  "audioSummary": {
    "speechRatio": 0.74,
    "avgRms": 0.041,
    "rmsCoV": 0.29,
    "wpm": 162,
    "pauseCount": 4,
    "avgPauseDurationMs": 1100,
    "maxPauseDurationMs": 3200,
    "responseLatencyMs": 1800,
    "endFadeOut": false,
    "estimatedFillerCount": 3,
    "fillerWordCount": 2,
    "wordCount": 47,
    "ttr": 0.61
  }
}
```

`audioSummary` 필드 설명:

| 필드 | 타입 | 설명 | 계산 주체 |
|------|------|------|----------|
| `speechRatio` | float | 전체 응답 시간 중 발화 비율 (`totalSpeechMs / responseTimeMs`) | 프론트 |
| `avgRms` | float | 발화 구간 평균 RMS | 프론트 (realtime.audio 누적) |
| `rmsCoV` | float | RMS 변동계수 (`σ / μ`), 음량 안정성 지표 | 프론트 (realtime.audio 누적) |
| `wpm` | number | 분당 단어 수 (`wordCount / (speechMs / 60000)`) | 프론트 (STT 결과 기반) |
| `pauseCount` | number | 500ms 이상 침묵 발생 횟수 | 프론트 |
| `avgPauseDurationMs` | number | 침묵 구간 평균 지속 시간 | 프론트 |
| `maxPauseDurationMs` | number | 최장 침묵 지속 시간 | 프론트 |
| `responseLatencyMs` | number | 질문 표시 ~ 첫 발화 감지까지 걸린 시간 | 프론트 |
| `endFadeOut` | boolean | 마지막 2초 RMS가 평균의 40% 미만인지 여부 | 프론트 |
| `estimatedFillerCount` | number | 300ms 미만 단발 발화 burst 수 (필러음 근사치) | 프론트 |
| `fillerWordCount` | number | STT 텍스트에서 검출된 필러 단어 수 ("음", "어", "그", "네", "뭐") | 프론트 (STT 기반) |
| `wordCount` | number | STT 전사 총 단어 수 | 프론트 (STT 기반) |
| `ttr` | float | 어휘 다양성 (`고유 단어 수 / wordCount`), 반복 표현 지표 | 프론트 (STT 기반) |

`audioSummary`가 없거나 `null`이면 백엔드는 음성 점수 항목을 산출하지 않고 건너뜁니다.

---

## SUBSCRIBE: 실시간 음성 피드백

구독:

```
/topic/realtime/{sessionId}/audio
```

백엔드가 `realtime.audio` 데이터를 수신하고 기준 이탈이 감지될 때 발행합니다. 정상 범위일 때는 이벤트를 보내지 않습니다.

서버 이벤트:

```json
{
  "type": "SPEECH_RATE_FEEDBACK",
  "data": {
    "status": "TOO_FAST",
    "wpm": 210,
    "message": "말하는 속도가 빠릅니다. 조금 천천히 말씀해보세요.",
    "targetWpmMin": 130,
    "targetWpmMax": 180
  }
}
```

`status` 값 정의:

| status | 조건 | 프론트 처리 |
|--------|------|------------|
| `OPTIMAL` | 정상 범위 | 이벤트 미발행 |
| `TOO_FAST` | WPM > 180 | 경고 배너 표시 |
| `TOO_SLOW` | WPM < 100 | 경고 배너 표시 |
| `TOO_QUIET` | avgRms < 0.01 | 볼륨 경고 배너 |
| `LONG_PAUSE` | 침묵 5초 초과 | 힌트 배너 |

WPM은 `realtime.audio` 스트림에서 누적된 `speechDurationMs`와 추정 음절 수로 근사합니다. 정밀 WPM은 `session.answer` 제출 이후 `audioSummary.wpm`으로 최종 확정됩니다.

---

## SEND: 실시간 스트림 종료 (기존 확장)

전송:

```
/app/realtime.end
```

기존 페이로드에 `includesAudio` 플래그를 추가합니다.

페이로드:

```json
{
  "sessionId": "123",
  "includesAudio": true
}
```

`includesAudio: true`이면 백엔드는 해당 세션의 누적 음성 특징 데이터를 사용해 최종 음성 리포트를 산출합니다.

---

## 백엔드 점수화 공식

`audioSummary` 기반으로 백엔드에서 식 기반 산출할 수 있는 항목입니다.

| 항목 | 공식 | 만점 기준 |
|------|------|----------|
| 말하기 속도 | `1 - abs(wpm - 155) / 55` (155 WPM 기준, 100~210 클램프) | 130~180 WPM = 100점 |
| 음량 안정성 | `max(0, 1 - rmsCoV / 0.5)` | rmsCoV < 0.2 = 100점 |
| 발화 비율 | `speechRatio`가 0.6~0.8 구간에 가까울수록 고점 | 0.7 = 100점 |
| 침묵 활용 | `pauseCount > 0` & `avgPauseDurationMs < 3000` 시 가산 | 전략적 침묵 |
| 필러 비율 | `max(0, 1 - fillerWordCount / wordCount * 10)` | fillerWordCount = 0 = 100점 |
| 어휘 다양성 | `ttr` 직접 사용 | 0.7 이상 = 100점 |
| 응답 즉응성 | `responseLatencyMs < 2000` ? 100 : `max(0, 100 - (latency - 2000) / 100)` | 2초 이내 = 100점 |
| 마무리 명확성 | `endFadeOut ? 60 : 100` | fade-out 없음 = 100점 |

질문별 점수를 가중 평균해 세션 전체 음성 종합 점수를 산출합니다.

---

## 프론트 구현 순서

기존 면접 진행 흐름에 아래를 추가합니다.

1. 카메라 권한 요청과 함께 마이크 권한을 요청합니다 (`getUserMedia({ audio: true })`).
2. AudioWorklet을 초기화하고 1초 주기 특징 집계 루프를 시작합니다.
3. Web Speech API (`SpeechRecognition`)를 시작하고 `onresult`에서 transcript를 로컬 변수에 누적합니다.
4. STOMP 연결 완료(`CONNECTED`) 후 `/topic/realtime/{sessionId}/audio`를 구독합니다.
5. 답변 중 AudioWorklet 집계값을 1초마다 `/app/realtime.audio`로 전송합니다.
6. `/topic/realtime/{sessionId}/audio` 이벤트 수신 시 기존 `showWarning` 배너를 재사용해 피드백을 표시합니다.
7. 답변 버튼 클릭 시 AudioWorklet 루프와 SpeechRecognition을 일시 중단하고 누적값으로 `audioSummary`를 계산합니다.
8. 기존 `/app/session.answer` 전송 시 `answer`(STT 텍스트)와 `audioSummary`를 함께 포함합니다.
9. 다음 질문이 오면 누적값을 초기화하고 AudioWorklet 루프와 SpeechRecognition을 재시작합니다.
10. `/app/realtime.end` 전송 시 `includesAudio: true`를 포함합니다.

