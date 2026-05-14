package com.interviewmirror.auth.service;

import com.interviewmirror.auth.dto.AuthResponse;
import com.interviewmirror.auth.dto.LoginRequest;
import com.interviewmirror.auth.dto.LogoutRequest;
import com.interviewmirror.auth.dto.MeResponse;
import com.interviewmirror.auth.dto.ReissueRequest;
import com.interviewmirror.auth.dto.SignupRequest;
import com.interviewmirror.auth.jwt.JwtTokenProvider;
import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.user.entity.User;
import com.interviewmirror.user.repository.UserRepository;
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
  private final RefreshTokenService refreshTokenService;

  @Transactional
  public AuthResponse signup(SignupRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
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
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));

    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.INVALID_LOGIN);
    }

    return createAuthResponse(user);
  }

  public AuthResponse reissue(ReissueRequest request) {
    String refreshToken = request.getRefreshToken();

    if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    Long userId = jwtTokenProvider.getUserId(refreshToken);
    refreshTokenService.validateStoredRefreshToken(userId, refreshToken);

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());

    return AuthResponse.builder()
        .accessToken(newAccessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .user(
            AuthResponse.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .build())
        .build();
  }

  public void logout(LogoutRequest request) {
    refreshTokenService.deleteRefreshToken(request.getRefreshToken());
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
    String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());

    refreshTokenService.saveRefreshToken(user.getId(), refreshToken);

    return AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
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
