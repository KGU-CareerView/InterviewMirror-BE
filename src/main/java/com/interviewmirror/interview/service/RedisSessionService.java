package com.interviewmirror.interview.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisSessionService {
  private final RedisTemplate<String, Object> redisTemplate;

  // 중복 질문 생성 방지 (NX 옵션 활용)
  public boolean lockQuestionGeneration(Long sessionId) {
    String key = "session:" + sessionId + ":is_processing";
    Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(key, "true", 30, TimeUnit.SECONDS);
    return Boolean.TRUE.equals(isLocked);
  }

  // 중복 요청 방지를 위해 걸어두었던 Lock(잠금)을 해제하는 메서드입니다.
  public void unlockQuestionGeneration(Long sessionId) {
    String key = "session:" + sessionId + ":is_processing";
    redisTemplate.delete(key);
  }

  // 세션 상태 업데이트 및 조회 등 로직
  public void updateSessionState(Long sessionId, String state) {
    String key = "session:" + sessionId;
    redisTemplate.opsForHash().put(key, "status", state);
    redisTemplate.expire(key, Duration.ofHours(2)); // TTL 갱신
  }

  // 질답 저장 (List 형태)
  public void addQaToRedis(Long sessionId, String qaJson) {
    redisTemplate.opsForList().rightPush("session:" + sessionId + ":qa", qaJson);
  }

  public List<String> getQaList(Long sessionId) {
    String key = "session:" + sessionId + ":qa";
    // 0부터 -1까지 조회하면 리스트의 전체 요소를 가져옵니다.
    List<Object> list = redisTemplate.opsForList().range(key, 0, -1);
    if (list == null) return List.of();
    return list.stream().map(Object::toString).collect(Collectors.toList());
  }

  // AI가 생성한 방금 전 최신 질문을 Redis에 저장 (TTL 2시간)
  public void setLastQuestion(Long sessionId, String question) {
    String key = "session:" + sessionId + ":last_question";
    redisTemplate.opsForValue().set(key, question, Duration.ofHours(2));
  }

  // 프론트엔드에서 답변만 왔을 때, 짝을 맞추기 위해 방금 전 질문 꺼내오기
  public String getLastQuestion(Long sessionId) {
    String key = "session:" + sessionId + ":last_question";
    Object question = redisTemplate.opsForValue().get(key);
    return question != null ? question.toString() : null;
  }

  // 현재 면접 세션 상태 조회
  public String getSessionState(Long sessionId) {
    String key = "session:" + sessionId;

    // Hash 구조에서 "status" 필드의 값을 꺼내옵니다.
    Object state = redisTemplate.opsForHash().get(key, "status");

    // 값이 존재하면 String으로 변환하여 반환하고, 없으면 null을 반환합니다.
    return state != null ? state.toString() : null;
  }

  public void clearQaList(Long sessionId) {
    String key = "session:" + sessionId + ":qa";
    redisTemplate.delete(key);
  }
}
