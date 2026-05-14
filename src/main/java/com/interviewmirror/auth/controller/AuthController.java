package com.interviewmirror.auth.controller;

import com.interviewmirror.auth.dto.AuthResponse;
import com.interviewmirror.auth.dto.LoginRequest;
import com.interviewmirror.auth.dto.LogoutRequest;
import com.interviewmirror.auth.dto.MeResponse;
import com.interviewmirror.auth.dto.ReissueRequest;
import com.interviewmirror.auth.dto.SignupRequest;
import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.auth.service.AuthService;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.common.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

  @PostMapping("/reissue")
  public ApiResponse<AuthResponse> reissue(@Valid @RequestBody ReissueRequest request) {
    AuthResponse response = authService.reissue(request);
    return ApiResponse.success(response);
  }

  @PostMapping("/logout")
  public ApiResponse<MessageResponse> logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request);
    return ApiResponse.success(new MessageResponse("Logout successful"));
  }

  @GetMapping("/me")
  public ApiResponse<MeResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
    MeResponse response = authService.me(userDetails);
    return ApiResponse.success(response);
  }
}
