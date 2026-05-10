package com.interviewmirror.exception;

import lombok.Getter;

public class InterviewException extends RuntimeException {
  @Getter private final ErrorCode errorCode;

  public InterviewException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }
}
