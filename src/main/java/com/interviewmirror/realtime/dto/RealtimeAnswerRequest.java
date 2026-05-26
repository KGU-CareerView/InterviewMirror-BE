package com.interviewmirror.realtime.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RealtimeAnswerRequest {

  @NotNull private Long sessionId;

  @NotNull private String answer;

  private Integer questionIndex;

  private String emotionResult;

  private Integer responseTimeSeconds;

  private AudioSummaryDto audioSummary;
}
