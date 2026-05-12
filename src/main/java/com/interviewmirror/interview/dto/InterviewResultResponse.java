package com.interviewmirror.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewResultResponse {
  private String result; // AI가 분석한 최종 면접 결과 데이터 (JSON 형태라면 String 또는 별도 객체)
}
