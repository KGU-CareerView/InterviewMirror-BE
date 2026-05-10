package com.interviewmirror.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MeResponse {

  private Long id;
  private String email;
  private String name;
}
