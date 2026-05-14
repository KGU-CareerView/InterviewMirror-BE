package com.interviewmirror.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LogoutRequest {

  @NotBlank(message = "Refresh token cannot be blank")
  private String refreshToken;
}
