package com.interviewmirror.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 존재하는 이메일입니다."),
  INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN", "잘못된 이메일 또는 비밀번호입니다."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "잘못된 입력입니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "허용되지 않은 메서드입니다."),
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "알 수 없는 오류가 발생했습니다."),
  AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "인증이 필요합니다."),
  NETWORK_DISCONNECTED(
      HttpStatus.SERVICE_UNAVAILABLE,
      "NETWORK_DISCONNECTED",
      "네트워크 연결이 끊어졌습니다. 재연결을 시도해주세요."), // WebSocket 연동시 활용
  SESSION_EXPIRED(HttpStatus.GONE, "SESSION_EXPIRED", "세션이 만료되었습니다. 다시 시작해주세요."),
  SESSION_ALREADY_CLOSED(HttpStatus.FORBIDDEN, "SESSION_ALREADY_CLOSED", "이미 종료된 세션입니다."),
  AI_RESPONSE_FAILED(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_FAILED", "AI 응답 생성 중 오류가 발생했습니다."),
  SERVER_INTERNAL_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
  INTERNAL_COMMUNICATION_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_COMMUNICATION_ERROR", "내부 통신 중 오류가 발생했습니다."),
  SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "해당 면접 세션을 찾을 수 없습니다."),
  REPORT_NOT_READY(HttpStatus.NOT_FOUND, "REPORT_NOT_READY", "아직 AI가 분석 중입니다. 잠시 후 다시 시도해주세요.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
