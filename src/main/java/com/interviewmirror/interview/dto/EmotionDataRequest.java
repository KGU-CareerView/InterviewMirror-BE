package com.interviewmirror.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EmotionDataRequest {
  private String data; // 안면 특징점 데이터 (필요시 JsonNode 또는 별도 객체로 변경)
}
