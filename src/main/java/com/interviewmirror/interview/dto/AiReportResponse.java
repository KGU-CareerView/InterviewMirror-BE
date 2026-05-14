package com.interviewmirror.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiReportResponse {
  private Long sessionId;
  private Integer totalScore;
  private String feedback;
  private String strengths;
  private String weaknesses;
  private String aiAnalysisJson;
}
