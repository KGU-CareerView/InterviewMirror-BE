package com.interviewmirror.interview.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewmirror.interview.service.RedisSessionService;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisSessionServiceTest {

  @Mock private RedisTemplate<String, Object> redisTemplate;

  @Mock private HashOperations<String, Object, Object> hashOperations;

  @Mock private ValueOperations<String, Object> valueOperations;

  @InjectMocks private RedisSessionService redisSessionService;

  @Test
  @DisplayName("세션 상태가 Redis에 정상적으로 업데이트 되는지 확인")
  void updateSessionState_Success() {
    // given
    Long sessionId = 1L;
    String newState = "START";
    String redisKey = "session:" + sessionId;

    // opsForHash() 호출 시 가짜 hashOperations 반환
    when(redisTemplate.opsForHash()).thenReturn(hashOperations);

    // 🚨 새로 추가된 부분: expire(TTL 갱신) 메서드 호출 시 true를 반환하도록 모킹 (NPE 방지)
    when(redisTemplate.expire(eq(redisKey), any(Duration.class))).thenReturn(true);

    // when
    redisSessionService.updateSessionState(sessionId, newState);

    // then
    verify(hashOperations).put(redisKey, "status", newState);
    verify(redisTemplate).expire(eq(redisKey), any(Duration.class)); // expire가 잘 호출되었는지 검증
  }

  @Test
  @DisplayName("AI 질문 생성 락(Lock) 획득 성공 테스트")
  void lockQuestionGeneration_Success() {
    // given
    Long sessionId = 1L;
    // 실제 코드와 완벽히 동일한 키와 값을 사용
    String lockKey = "session:" + sessionId + ":is_processing";

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // 🚨 실제 코드에 맞춰 "true", 30L, TimeUnit.SECONDS 로 정확히 매핑!
    when(valueOperations.setIfAbsent(eq(lockKey), eq("true"), eq(30L), eq(TimeUnit.SECONDS)))
        .thenReturn(true);

    // when
    boolean isLocked = redisSessionService.lockQuestionGeneration(sessionId);

    // then
    assertTrue(isLocked);
    verify(valueOperations).setIfAbsent(eq(lockKey), eq("true"), eq(30L), eq(TimeUnit.SECONDS));
  }
}
