package com.interviewmirror.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewmirror.domain.auth.dto.AuthResponse;
import com.interviewmirror.domain.auth.dto.LoginRequest;
import com.interviewmirror.domain.auth.dto.SignupRequest;
import com.interviewmirror.domain.auth.jwt.JwtTokenProvider;
import com.interviewmirror.domain.user.entity.User;
import com.interviewmirror.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private BCryptPasswordEncoder passwordEncoder;

  @Mock private JwtTokenProvider jwtTokenProvider;

  @InjectMocks private AuthService authService;

  @Test
  @DisplayName("회원가입 성공 시 access token과 사용자 정보를 반환한다")
  void signupSuccess() {
    SignupRequest request = new SignupRequest();
    ReflectionTestUtils.setField(request, "email", "test@example.com");
    ReflectionTestUtils.setField(request, "name", "홍길동");
    ReflectionTestUtils.setField(request, "password", "password1234");

    User savedUser =
        User.builder()
            .id(1L)
            .email("test@example.com")
            .name("홍길동")
            .passwordHash("encodedPassword")
            .build();

    when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password1234")).thenReturn("encodedPassword");
    when(userRepository.save(any(User.class))).thenReturn(savedUser);
    when(jwtTokenProvider.createAccessToken(1L, "test@example.com")).thenReturn("access-token");

    AuthResponse response = authService.signup(request);

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getUser().getId()).isEqualTo(1L);
    assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
    assertThat(response.getUser().getName()).isEqualTo("홍길동");

    verify(userRepository).existsByEmail("test@example.com");
    verify(passwordEncoder).encode("password1234");
    verify(userRepository).save(any(User.class));
    verify(jwtTokenProvider).createAccessToken(1L, "test@example.com");
  }

  @Test
  @DisplayName("로그인 성공 시 access token과 사용자 정보를 반환한다")
  void loginSuccess() {
    LoginRequest request = new LoginRequest();
    ReflectionTestUtils.setField(request, "email", "test@example.com");
    ReflectionTestUtils.setField(request, "password", "password1234");

    User user =
        User.builder()
            .id(1L)
            .email("test@example.com")
            .name("홍길동")
            .passwordHash("encodedPassword")
            .build();

    when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password1234", "encodedPassword")).thenReturn(true);
    when(jwtTokenProvider.createAccessToken(1L, "test@example.com")).thenReturn("access-token");

    AuthResponse response = authService.login(request);

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getTokenType()).isEqualTo("Bearer");
    assertThat(response.getUser().getId()).isEqualTo(1L);
    assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
    assertThat(response.getUser().getName()).isEqualTo("홍길동");

    verify(userRepository).findByEmail("test@example.com");
    verify(passwordEncoder).matches("password1234", "encodedPassword");
    verify(jwtTokenProvider).createAccessToken(1L, "test@example.com");
  }
}
