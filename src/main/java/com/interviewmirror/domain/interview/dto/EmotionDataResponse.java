package com.interviewmirror.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionDataResponse {
  private String message; // 처리 메시지
  private String result; // 분석 결과
}
