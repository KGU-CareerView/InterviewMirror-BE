package com.interviewmirror.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AnswerSubmitRequest {
  private String answer; // 사용자의 답변 텍스트
  private String emotionResult; // 감정 분석 결과 (예: JSON String 또는 가장 높은 감정 텍스트)
  private Integer responseTimeSeconds; // 답변에 걸린 시간 (초 단위)
}
