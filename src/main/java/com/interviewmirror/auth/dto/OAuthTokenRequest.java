package com.interviewmirror.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OAuthTokenRequest {

  @NotBlank(message = "OAuth code cannot be blank")
  private String code;
}
