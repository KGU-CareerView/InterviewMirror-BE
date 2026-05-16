package com.interviewmirror.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 존재하는 이메일입니다."),
  INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN", "잘못된 이메일 또는 비밀번호입니다."),
  INVALID_REFRESH_TOKEN(
      HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 refresh token입니다."),
  REFRESH_TOKEN_NOT_FOUND(
      HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_NOT_FOUND", "refresh token을 찾을 수 없습니다."),
  INVALID_OAUTH_CODE(HttpStatus.UNAUTHORIZED, "INVALID_OAUTH_CODE", "유효하지 않은 OAuth code입니다."),
  OAUTH_EMAIL_NOT_FOUND(
      HttpStatus.BAD_REQUEST, "OAUTH_EMAIL_NOT_FOUND", "OAuth 계정에서 이메일을 찾을 수 없습니다."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "잘못된 입력입니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "허용되지 않은 메서드입니다."),
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "알 수 없는 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
