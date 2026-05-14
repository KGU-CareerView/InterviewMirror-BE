package com.interviewmirror.auth.service;

import com.interviewmirror.auth.jwt.JwtTokenProvider;
import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final String REFRESH_TOKEN_PREFIX = "refresh:";

  private final StringRedisTemplate stringRedisTemplate;
  private final JwtTokenProvider jwtTokenProvider;

  public void saveRefreshToken(Long userId, String refreshToken) {
    String key = createKey(userId);

    stringRedisTemplate
        .opsForValue()
        .set(key, refreshToken, Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpirationMs()));
  }

  public void validateStoredRefreshToken(Long userId, String refreshToken) {
    if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    String storedRefreshToken = stringRedisTemplate.opsForValue().get(createKey(userId));

    if (storedRefreshToken == null) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    if (!storedRefreshToken.equals(refreshToken)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
  }

  public void deleteRefreshToken(String refreshToken) {
    if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    Long userId = jwtTokenProvider.getUserId(refreshToken);
    stringRedisTemplate.delete(createKey(userId));
  }

  private String createKey(Long userId) {
    return REFRESH_TOKEN_PREFIX + userId;
  }
}
