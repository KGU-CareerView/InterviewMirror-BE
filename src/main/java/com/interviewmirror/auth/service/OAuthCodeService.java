package com.interviewmirror.auth.service;

import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthCodeService {

  private static final String OAUTH_CODE_PREFIX = "oauth:code:";

  private final StringRedisTemplate stringRedisTemplate;

  @Value("${oauth.code-expiration-ms}")
  private long oauthCodeExpirationMs;

  public String saveCode(Long userId) {
    String code = UUID.randomUUID().toString();
    String key = createKey(code);

    stringRedisTemplate
        .opsForValue()
        .set(key, String.valueOf(userId), Duration.ofMillis(oauthCodeExpirationMs));

    return code;
  }

  public Long consumeCode(String code) {
    String key = createKey(code);
    String userId = stringRedisTemplate.opsForValue().get(key);

    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_OAUTH_CODE);
    }

    stringRedisTemplate.delete(key);
    return Long.valueOf(userId);
  }

  private String createKey(String code) {
    return OAUTH_CODE_PREFIX + code;
  }
}
