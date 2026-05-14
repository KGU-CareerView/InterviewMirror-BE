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

  FEEDBACK_FRAME_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK_FRAME_FAILED", "피드백 프레임 분석에 실패했습니다."),
  FEEDBACK_COMPLETE_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK_COMPLETE_FAILED", "피드백 세션 완료 처리에 실패했습니다."),
  FEEDBACK_STREAM_SEND_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK_STREAM_SEND_FAILED", "AI 서버로 피드백 프레임 전송에 실패했습니다."),
  AI_STREAM_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "AI_STREAM_FAILED", "AI 분석 스트림 처리에 실패했습니다."),
  FEEDBACK_SERIALIZE_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK_SERIALIZE_FAILED", "피드백 응답 직렬화에 실패했습니다."),
  FEEDBACK_DESERIALIZE_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK_DESERIALIZE_FAILED", "피드백 응답 역직렬화에 실패했습니다."),

  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "잘못된 입력입니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "허용되지 않은 메서드입니다."),
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "알 수 없는 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
