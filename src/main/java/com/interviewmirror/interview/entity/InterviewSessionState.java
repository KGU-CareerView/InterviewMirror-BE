package com.interviewmirror.interview.entity;

import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import java.util.Locale;

public enum InterviewSessionState {
  READY,
  PREPARING,
  IN_PROGRESS,
  PAUSED,
  ENDED;

  public static InterviewSessionState from(String value) {
    if (value == null) {
      throw new InterviewException(ErrorCode.VALIDATION_ERROR);
    }

    try {
      return InterviewSessionState.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InterviewException(ErrorCode.VALIDATION_ERROR);
    }
  }
}
