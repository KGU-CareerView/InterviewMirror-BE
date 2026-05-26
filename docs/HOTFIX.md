# 백엔드 변경 요청 — `/app/realtime.audio` 페이로드 구조 변경

## 배경

기존에는 프론트엔드가 1초 인터벌마다 AudioWorklet에서 받은 **가장 최신 window 1개**만 `features` 필드로 전송했습니다.
AudioWorklet은 약 1초(sampleRate 샘플)마다 window 1개를 생성하지만, 인터벌 주기와 worklet 주기가 어긋날 경우 중간 window가 유실되었습니다.

이를 해결하기 위해 **직전 전송 이후 누적된 모든 window를 배열로 묶어 한 번에 전송**하는 방식으로 변경하였습니다.

---

## 변경 내용

### 변경 전

```json
{
  "sessionId": 22,
  "userId": "3",
  "timestamp": 1779695283244,
  "questionIndex": 3,
  "windowMs": 1000,
  "features": {
    "rms": 0.0312,
    "zeroCrossingRate": 0.1045,
    "isSpeaking": true,
    "speechDurationMs": 780,
    "silenceDurationMs": 220,
    "peakAmplitude": 0.2841
  }
}
```

### 변경 후

```json
{
  "sessionId": 22,
  "userId": "3",
  "timestamp": 1779695283244,
  "questionIndex": 3,
  "windowMs": 1000,
  "windows": [
    {
      "rms": 0.0278,
      "zeroCrossingRate": 0.0982,
      "isSpeaking": false,
      "speechDurationMs": 120,
      "silenceDurationMs": 880,
      "peakAmplitude": 0.1203
    },
    {
      "rms": 0.0312,
      "zeroCrossingRate": 0.1045,
      "isSpeaking": true,
      "speechDurationMs": 780,
      "silenceDurationMs": 220,
      "peakAmplitude": 0.2841
    }
  ]
}
```

**변경 요약:**
- `features` (단일 객체) → `windows` (배열)
- 정상 상황에서 배열 길이는 1이지만, 누적된 경우 2개 이상일 수 있음
- `windows`가 비어있으면 전송 자체를 하지 않으므로 빈 배열은 수신되지 않음

---

## AudioWindow 객체 필드

AudioWorklet(`public/worklets/audio-processor.js`)이 약 1초(sampleRate 샘플) 단위로 집계해 생성하는 window입니다.

| 필드                | 타입      | 설명                                                                        |
| ------------------- | --------- | --------------------------------------------------------------------------- |
| `rms`               | `number`  | 창 평균 RMS (음량 에너지, 소수점 4자리)                                     |
| `zeroCrossingRate`  | `number`  | 창 평균 영교차율 (고주파/노이즈 비율, 소수점 4자리)                         |
| `isSpeaking`        | `boolean` | 발화 판정 — 창 내 발화 샘플이 전체의 50% 초과이면 `true`                    |
| `speechDurationMs`  | `number`  | 창 내 발화 구간 합산 (ms), 최대 약 `windowMs`(1000ms)                       |
| `silenceDurationMs` | `number`  | 창 내 침묵 구간 합산 (ms), `speechDurationMs + silenceDurationMs ≒ windowMs` |
| `peakAmplitude`     | `number`  | 창 내 최대 진폭 (소수점 4자리)                                              |

---

## 백엔드 처리 방식

배열 내 window들을 집계하여 기존 단일 `features` 처리 로직에 그대로 전달할 수 있습니다.

### 집계 규칙

| 필드                | 집계 방법                    |
| ------------------- | ----------------------------|
| `rms`               | 평균                         |
| `zeroCrossingRate`  | 평균                         |
| `isSpeaking`        | OR (하나라도 true이면 true)  |
| `speechDurationMs`  | 합산                         |
| `silenceDurationMs` | 합산                         |
| `peakAmplitude`     | 최댓값                       |

### Kotlin 구현 예시

```kotlin
data class AudioWindow(
    val rms: Double = 0.0,
    val zeroCrossingRate: Double = 0.0,
    val isSpeaking: Boolean = false,
    val speechDurationMs: Long = 0,
    val silenceDurationMs: Long = 0,
    val peakAmplitude: Double = 0.0,
)

data class RealtimeAudioPayload(
    val sessionId: Long,
    val userId: String,
    val timestamp: Long,
    val questionIndex: Int,
    val windowMs: Long,
    val windows: List<AudioWindow>,
)

fun List<AudioWindow>.aggregate(): AudioWindow {
    require(isNotEmpty())
    return AudioWindow(
        rms               = sumOf { it.rms } / size,
        zeroCrossingRate  = sumOf { it.zeroCrossingRate } / size,
        isSpeaking        = any { it.isSpeaking },
        speechDurationMs  = sumOf { it.speechDurationMs },
        silenceDurationMs = sumOf { it.silenceDurationMs },
        peakAmplitude     = maxOf { it.peakAmplitude },
    )
}

// 기존 처리 로직 호환 예시
fun handleRealtimeAudio(payload: RealtimeAudioPayload) {
    val features = payload.windows.aggregate()
    processAudioFeatures(payload.sessionId, payload.questionIndex, features)
}
```

---

## 변경이 필요한 백엔드 파일

STOMP 메시지 핸들러에서 `/app/realtime.audio` 수신 시 DTO의 `features` 단일 객체 필드를 `windows: List<AudioWindow>`로 교체하고, 집계 후 기존 분석 파이프라인으로 전달하면 됩니다.

> **참고**: 백엔드가 업데이트되기 전까지 현재 프론트가 `windows` 배열을 전송하므로, 기존 DTO의 `rms`, `zcr` 등 flat 필드는 모두 0 또는 기본값으로 수신됩니다.

