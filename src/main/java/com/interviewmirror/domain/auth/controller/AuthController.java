package com.interviewmirror.domain.auth.controller;

import com.interviewmirror.domain.auth.dto.AuthResponse;
import com.interviewmirror.domain.auth.dto.LoginRequest;
import com.interviewmirror.domain.auth.dto.MeResponse;
import com.interviewmirror.domain.auth.dto.SignupRequest;
import com.interviewmirror.domain.auth.security.CustomUserDetails;
import com.interviewmirror.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        MeResponse response = authService.me(userDetails);
        return ResponseEntity.ok(response);
    }
}