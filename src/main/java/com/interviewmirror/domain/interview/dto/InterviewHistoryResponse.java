package com.interviewmirror.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewHistoryResponse {
    private List<Long> sessionIds; // 사용자의 과거 세션 ID 목록
}
