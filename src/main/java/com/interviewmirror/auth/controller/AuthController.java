package com.interviewmirror.auth.controller;

import com.interviewmirror.auth.dto.AuthResponse;
import com.interviewmirror.auth.dto.LoginRequest;
import com.interviewmirror.auth.dto.MeResponse;
import com.interviewmirror.auth.dto.SignupRequest;
import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.auth.service.AuthService;
import com.interviewmirror.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
    AuthResponse response = authService.signup(request);
    return ApiResponse.success(response);
  }

  @PostMapping("/login")
  public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return ApiResponse.success(response);
  }

  @GetMapping("/me")
  public ApiResponse<MeResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
    MeResponse response = authService.me(userDetails);
    return ApiResponse.success(response);
  }
}
