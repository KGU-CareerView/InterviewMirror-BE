package com.interviewmirror.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCreateResponse {
    private Long sessionId;
    private LocalDateTime date; // 유효기간
    private String sessionState; // 초기 세션 상태
}