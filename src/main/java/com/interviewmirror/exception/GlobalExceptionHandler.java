package com.interviewmirror.exception;

import com.interviewmirror.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
    log.error("Business exception: {}", e.getMessage());

    ErrorCode errorCode = e.getErrorCode();

    return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException e) {
    log.error("Validation exception: {}", e.getMessage());

    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Invalid input");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR, message));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(
      HttpRequestMethodNotSupportedException e) {
    log.warn("Method not supported: {}", e.getMessage());

    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED, e.getMessage()));
  }

  @ExceptionHandler(InterviewException.class)
  public ResponseEntity<ApiResponse<Void>> handleCustomException(InterviewException e) {
    log.error("Interview exception: {}", e.getErrorCode().getMessage());

    return ResponseEntity.status(e.getErrorCode().getStatus())
        .body(ApiResponse.fail(e.getErrorCode()));
  }
}
