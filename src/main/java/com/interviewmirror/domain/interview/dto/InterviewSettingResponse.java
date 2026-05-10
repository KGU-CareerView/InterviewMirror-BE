package com.interviewmirror.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 면접 사전설정 응답 DTO
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSettingResponse {
  private Long settingId;
  private String message;
}
