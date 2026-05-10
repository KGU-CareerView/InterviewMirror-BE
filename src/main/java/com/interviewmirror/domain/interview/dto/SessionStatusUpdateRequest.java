package com.interviewmirror.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatusUpdateRequest {
  private String status; // START, PAUSE, RESUME, END
}
