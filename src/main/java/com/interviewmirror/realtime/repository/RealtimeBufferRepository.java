package com.interviewmirror.realtime.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.realtime.dto.RealtimeResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RealtimeBufferRepository {

  private static final String KEY_PREFIX = "realtime:frames:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Value("${realtime.buffer.ttl:PT2H}")
  private Duration ttl;

  public void appendAll(String sessionId, Collection<RealtimeResponse> responses) {
    if (responses.isEmpty()) {
      return;
    }

    List<String> payloads = responses.stream().map(this::serialize).toList();

    String key = key(sessionId);
    redisTemplate.opsForList().rightPushAll(key, payloads);
    redisTemplate.expire(key, ttl);
  }

  public List<RealtimeResponse> findAll(String sessionId) {
    List<String> payloads = redisTemplate.opsForList().range(key(sessionId), 0, -1);
    if (payloads == null || payloads.isEmpty()) {
      return Collections.emptyList();
    }

    return payloads.stream().filter(Objects::nonNull).map(this::deserialize).toList();
  }

  public void delete(String sessionId) {
    redisTemplate.delete(key(sessionId));
  }

  private String key(String sessionId) {
    return KEY_PREFIX + sessionId;
  }

  private String serialize(RealtimeResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.REALTIME_SERIALIZE_FAILED, e);
    }
  }

  private RealtimeResponse deserialize(String payload) {
    try {
      return objectMapper.readValue(payload, RealtimeResponse.class);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.REALTIME_DESERIALIZE_FAILED, e);
    }
  }
}
