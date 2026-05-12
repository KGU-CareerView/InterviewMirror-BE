package com.interviewmirror.interview.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewHistoryResponse {
  private List<Long> sessionIds; // 사용자의 과거 세션 ID 목록
}
