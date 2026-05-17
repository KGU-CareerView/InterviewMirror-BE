package com.interviewmirror.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 답변 팁 생성 응답 DTO

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerTipResponse {
  private String tip; // AI가 생성한 답변 요령 / 팁
}
