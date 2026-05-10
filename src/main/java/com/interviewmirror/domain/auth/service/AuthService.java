package com.interviewmirror.domain.auth.service;

import com.interviewmirror.domain.auth.dto.AuthResponse;
import com.interviewmirror.domain.auth.dto.LoginRequest;
import com.interviewmirror.domain.auth.dto.MeResponse;
import com.interviewmirror.domain.auth.dto.SignupRequest;
import com.interviewmirror.domain.auth.jwt.JwtTokenProvider;
import com.interviewmirror.domain.auth.security.CustomUserDetails;
import com.interviewmirror.domain.user.entity.User;
import com.interviewmirror.domain.user.repository.UserRepository;
import com.interviewmirror.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  @Transactional
  public AuthResponse signup(SignupRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new BusinessException("Email already exists", "DUPLICATE_EMAIL");
    }

    User user =
        User.builder()
            .email(request.getEmail())
            .name(request.getName())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .build();

    User savedUser = userRepository.save(user);
    log.info("User signed up: {}", savedUser.getId());

    return createAuthResponse(savedUser);
  }

  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new BusinessException("Invalid email or password", "INVALID_LOGIN"));

    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new BusinessException("Invalid email or password", "INVALID_LOGIN");
    }

    return createAuthResponse(user);
  }

  public MeResponse me(CustomUserDetails userDetails) {
    return MeResponse.builder()
        .id(userDetails.getId())
        .email(userDetails.getEmail())
        .name(userDetails.getName())
        .build();
  }

  private AuthResponse createAuthResponse(User user) {
    String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());

    return AuthResponse.builder()
        .accessToken(accessToken)
        .tokenType("Bearer")
        .user(
            AuthResponse.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .build())
        .build();
  }
}
