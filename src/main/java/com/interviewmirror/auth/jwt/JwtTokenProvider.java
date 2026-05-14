package com.interviewmirror.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class JwtTokenProvider {

  private static final String TOKEN_TYPE_CLAIM = "type";
  private static final String ACCESS_TOKEN_TYPE = "ACCESS";
  private static final String REFRESH_TOKEN_TYPE = "REFRESH";

  private final SecretKey secretKey;
  private final long accessTokenExpirationMs;
  private final long refreshTokenExpirationMs;

  public JwtTokenProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
      @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpirationMs = accessTokenExpirationMs;
    this.refreshTokenExpirationMs = refreshTokenExpirationMs;
  }

  public String createAccessToken(Long userId, String email) {
    return createToken(userId, email, ACCESS_TOKEN_TYPE, accessTokenExpirationMs);
  }

  public String createRefreshToken(Long userId, String email) {
    return createToken(userId, email, REFRESH_TOKEN_TYPE, refreshTokenExpirationMs);
  }

  public Long getUserId(String token) {
    return Long.valueOf(parseClaims(token).getSubject());
  }

  public boolean validateAccessToken(String token) {
    return validateTokenType(token, ACCESS_TOKEN_TYPE);
  }

  public boolean validateRefreshToken(String token) {
    return validateTokenType(token, REFRESH_TOKEN_TYPE);
  }

  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (Exception e) {
      log.warn("JWT validation failed: {}", e.getMessage());
      return false;
    }
  }

  private String createToken(Long userId, String email, String tokenType, long expirationMs) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim(TOKEN_TYPE_CLAIM, tokenType)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(secretKey)
        .compact();
  }

  private boolean validateTokenType(String token, String expectedType) {
    try {
      Claims claims = parseClaims(token);
      return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    } catch (Exception e) {
      log.warn("JWT {} token validation failed: {}", expectedType, e.getMessage());
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }
}
