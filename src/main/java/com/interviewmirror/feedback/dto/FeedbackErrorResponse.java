package com.interviewmirror.domain.feedback.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackErrorResponse {

  private String sessionId;
  private String message;
  private String errorCode;
  private LocalDateTime timestamp;
}
