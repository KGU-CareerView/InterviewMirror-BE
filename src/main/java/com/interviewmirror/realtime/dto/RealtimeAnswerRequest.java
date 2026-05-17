package com.interviewmirror.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RealtimeAnswerRequest {

  @NotNull private Long sessionId;

  @NotBlank private String answer;

  private String emotionResult;

  private Integer responseTimeSeconds;
}
