package com.interviewmirror.interview.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InterviewSettingDetailResponse {
  private Long settingId;
  private String category;
  private String interviewType;
  private String difficulty;
  private Integer questionCount;
  private Integer timePerQuestion;
  private String resumeContent;
}
