package com.interviewmirror.realtime.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RealtimeAnswerRequest {

  @NotNull private Long sessionId;

  private String question;

  @NotNull private String answer;

  private Integer questionIndex;

  private String emotionResult;

  private Integer responseTimeSeconds;

  private AudioSummaryDto audioSummary;

  // true인 경우에만(=초기 질문 목록의 마지막 질문 답변) AI 꼬리물기 질문을 생성한다.
  private Boolean requestNextQuestion;
}
