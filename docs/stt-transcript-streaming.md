# STT Transcript 실시간 스트리밍 처리 구현 명세

## 배경

프론트엔드의 Web Speech API(STT)는 `session.answer` 전송 시점에 따라 transcript가 비어있을 수 있음.
이를 보완하기 위해 매 1초마다 전송되는 `/app/realtime.audio` 페이로드에 현재 누적된 STT transcript를
포함시키고, 백엔드가 이를 Redis에 축적 → `session.answer` 수신 시 축적된 transcript를 우선 사용.

## 프론트엔드 → 백엔드 페이로드 변경

`/app/realtime.audio` 에 `transcript` 필드 추가 (이미 적용됨):

```json
{
  "sessionId": "31",
  "userId": "1",
  "timestamp": 1748123456789,
  "questionIndex": 2,
  "windowMs": 1000,
  "windows": [...],
  "transcript": "저는 사용자 중심 설계를..."
}
```

- `transcript`: `currentTranscript`(isFinal 확정) + `pendingInterim`(현재 인식 중) 합산 문자열
- 값이 없으면 빈 문자열 `""` 전송
- 질문이 바뀌면 프론트에서 초기화되므로 항상 현재 질문에 대한 누적값

---

## 백엔드 구현 명세

### 1. `RealtimeAudioRequest` DTO에 `transcript` 필드 추가

**파일**: `realtime/dto/RealtimeAudioRequest.java`

```java
// 기존 필드들 유지, 아래 추가
private String transcript; // nullable — 빈 문자열이면 무시
```

---

### 2. `RedisSessionService`에 transcript 누적 메서드 추가

**파일**: `interview/service/RedisSessionService.java`

Redis Key 패턴: `session:{sessionId}:transcript:{questionIndex}`

```java
/**
 * realtime.audio 수신 시마다 해당 질문의 최신 transcript를 덮어씀.
 * 마지막으로 수신된 값이 가장 완성된 transcript이므로 SET(덮어쓰기)으로 처리.
 * TTL: 2시간 (세션 유지 시간과 동일)
 */
public void setTranscript(Long sessionId, int questionIndex, String transcript) {
    String key = "session:" + sessionId + ":transcript:" + questionIndex;
    redisTemplate.opsForValue().set(key, transcript, Duration.ofHours(2));
}

/**
 * session.answer 수신 시 해당 질문의 축적된 transcript 조회.
 * 값이 없으면 null 반환 → 호출부에서 answer 필드 원본 사용.
 */
public String getTranscript(Long sessionId, int questionIndex) {
    String key = "session:" + sessionId + ":transcript:" + questionIndex;
    Object value = redisTemplate.opsForValue().get(key);
    return value != null ? value.toString() : null;
}
```

---

### 3. `RealtimeService.analyzeAudio()`에 transcript 저장 로직 추가

**파일**: `realtime/service/RealtimeService.java`

기존 `analyzeAudio()` 메서드 내부에 transcript 저장 블록 추가:

```java
public void analyzeAudio(RealtimeAudioRequest request) {
    // ... 기존 silence/volume 피드백 로직 유지 ...

    // transcript 축적: 비어있지 않을 때만 저장
    String transcript = request.getTranscript();
    if (transcript != null && !transcript.isBlank()) {
        redisSessionService.setTranscript(
            Long.parseLong(request.getSessionId()),
            request.getQuestionIndex(),
            transcript
        );
    }
}
```

---

### 4. `RealtimeService.submitAnswer()`에서 transcript 우선 사용

**파일**: `realtime/service/RealtimeService.java`

`session.answer` 수신 시 현재 questionIndex를 알아야 하므로,
`RedisSessionService`에서 현재 질문 인덱스를 조회하거나
`RealtimeAnswerRequest`에 `questionIndex`를 추가하는 방식 중 하나 선택.

#### 방법 A — `RealtimeAnswerRequest`에 `questionIndex` 추가 (권장)

`RealtimeAnswerRequest`에 필드 추가:
```java
private Integer questionIndex; // 프론트의 questionIndex.value
```

프론트엔드 `submitCurrentAnswer()`에서 `questionIndex: questionIndex.value` 함께 전송.

`submitAnswer()` 수정:
```java
public void submitAnswer(RealtimeAnswerRequest request) {
    // Redis에 축적된 transcript를 우선 사용, 없으면 request.getAnswer() 원본 사용
    String answer = request.getAnswer();
    if (request.getQuestionIndex() != null) {
        String accumulated = redisSessionService.getTranscript(
            request.getSessionId(), request.getQuestionIndex()
        );
        if (accumulated != null && !accumulated.isBlank()) {
            answer = accumulated;
            log.info("Using accumulated STT transcript for answer: sessionId={} questionIndex={} length={}",
                request.getSessionId(), request.getQuestionIndex(), answer.length());
        }
    }

    String previousQuestion = sessionService.recordAnswer(
        request.getSessionId(),
        answer,                        // ← 축적된 transcript 사용
        request.getEmotionResult(),
        request.getResponseTimeSeconds(),
        request.getAudioSummary()
    );

    questionGenerationService.generateFollowUpQuestion(
        request.getSessionId(), previousQuestion, answer
    );
}
```

#### 방법 B — Redis에서 현재 questionIndex 별도 관리

`session.answer` 요청에 index를 추가하기 어려운 경우, Redis에
`session:{sessionId}:current_question_index` 키를 유지하고
`NEXT_QUESTION` 발행 시 증가시키는 방법도 가능.
단, 동시성 이슈가 생길 수 있어 방법 A를 우선 권장.

---

### 5. (선택) transcript Redis 키 정리

세션 종료(`realtime.end`) 처리 시 또는 TTL 만료로 자동 삭제됨.
명시적 삭제가 필요하면 `RealtimeService.completeSession()` 내에 추가:

```java
// completeSession() 내부 — 필요 시 추가
// transcript 키는 TTL(2h)로 자동 만료되므로 생략 가능
```

---

## 데이터 흐름 요약

```
[프론트엔드]
  매 1초: realtime.audio → { ..., transcript: "누적 STT" }
  버튼 클릭: session.answer → { ..., questionIndex: N, answer: "STT 또는 fallback" }

[백엔드 Redis]
  realtime.audio 수신 시:
    SET session:{id}:transcript:{qIdx} = "누적 STT"  (덮어쓰기, TTL 2h)

  session.answer 수신 시:
    GET session:{id}:transcript:{qIdx}
    → 값 있음: 축적 transcript 사용
    → 값 없음: request.answer 원본 사용 (fallback 포함)
```

---

## 변경 파일 목록

| 파일 | 변경 유형 |
|------|----------|
| `realtime/dto/RealtimeAudioRequest.java` | `transcript: String` 필드 추가 |
| `realtime/dto/RealtimeAnswerRequest.java` | `questionIndex: Integer` 필드 추가 |
| `interview/service/RedisSessionService.java` | `setTranscript()`, `getTranscript()` 메서드 추가 |
| `realtime/service/RealtimeService.java` | `analyzeAudio()`에 저장 로직, `submitAnswer()`에 우선 사용 로직 추가 |
| `realtime/controller/RealtimeWebSocketController.java` | 로그에 `transcript` 필드 포함 (선택) |
| `src/pages/InterviewView.vue` (프론트) | `session.answer` 전송 시 `questionIndex` 추가 |
