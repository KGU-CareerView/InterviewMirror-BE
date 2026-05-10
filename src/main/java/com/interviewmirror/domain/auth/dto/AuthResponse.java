package com.interviewmirror.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

  private String accessToken;
  private String tokenType;
  private UserInfo user;

  @Getter
  @Builder
  public static class UserInfo {
    private Long id;
    private String email;
    private String name;
  }
}
