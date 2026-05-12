package com.interviewmirror.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.interviewmirror.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class ApiResponse<T> {

  private final boolean success;
  private final String code;
  private final String message;
  private final T data;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  private final LocalDateTime timestamp;

  private ApiResponse(boolean success, String code, String message, T data) {
    this.success = success;
    this.code = code;
    this.message = message;
    this.data = data;
    this.timestamp = LocalDateTime.now();
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, null, "Success", data);
  }

  public static ApiResponse<Void> fail(ErrorCode errorCode) {
    return fail(errorCode, errorCode.getMessage());
  }

  public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
    return new ApiResponse<>(false, errorCode.getCode(), message, null);
  }
}
