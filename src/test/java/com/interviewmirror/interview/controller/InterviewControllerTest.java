package com.interviewmirror.interview.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.interviewmirror.auth.jwt.JwtTokenProvider;
import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.auth.service.AuthService;
import com.interviewmirror.infrastructure.RabbitMQProducer;
import com.interviewmirror.infrastructure.S3Service;
import com.interviewmirror.interview.dto.InterviewReportResponse;
import com.interviewmirror.interview.dto.InterviewResultResponse;
import com.interviewmirror.interview.service.InterviewPreparationService;
import com.interviewmirror.interview.service.InterviewService;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.realtime.client.AiGrpcClient;
import com.interviewmirror.user.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InterviewController.class)
@AutoConfigureMockMvc(addFilters = false)
public class InterviewControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InterviewService interviewService;
  @MockitoBean private SessionService sessionService;
  @MockitoBean private InterviewPreparationService interviewPreparationService;
  @MockitoBean private RedisSessionService redisSessionService;
  @MockitoBean private S3Service s3Service;
  @MockitoBean private AiGrpcClient aiGrpcClient;
  @MockitoBean private RabbitMQProducer rabbitMQProducer;
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private CustomUserDetails customUserDetails;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private AuthService authService;

  @TestConfiguration
  static class SecurityTestConfig
      implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {

    @org.springframework.beans.factory.annotation.Autowired
    private CustomUserDetails customUserDetails;

    @Override
    public void addArgumentResolvers(
        List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
      resolvers.add(
          new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
              return parameter.getParameterType().isAssignableFrom(CustomUserDetails.class);
            }

            @Override
            public Object resolveArgument(
                org.springframework.core.MethodParameter parameter,
                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                org.springframework.web.context.request.NativeWebRequest webRequest,
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
              return customUserDetails;
            }
          });
    }
  }

  @Test
  @DisplayName("면접 결과 조회 API 성공 (200 OK)")
  void getInterviewResult() throws Exception {
    // given
    Long sessionId = 1L;
    Long userId = 1L;
    given(customUserDetails.getId()).willReturn(userId);

    InterviewResultResponse mockResponse =
        InterviewResultResponse.builder()
            .sessionId(sessionId)
            .emotionGraph("{\"happy\": 0.8}")
            .details(Collections.emptyList()) // Null 방지
            .build();

    given(interviewService.getInterviewResult(eq(sessionId), anyLong())).willReturn(mockResponse);

    // when & then
    mockMvc
        .perform(
            get("/v1/interviews/{sessionID}/result", sessionId)
                .with(user(customUserDetails)) // 인증 정보 주입
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        // 💡 [수정됨] JSON 검증 경로도 DTO 필드명에 맞게 변경 (result -> emotionGraph 등)
        .andExpect(jsonPath("$.data.sessionId").value(sessionId))
        .andExpect(jsonPath("$.data.emotionGraph").value("{\"happy\": 0.8}"));
  }

  @Test
  @DisplayName("과거 면접 기록 조회 API 성공 (200 OK)")
  void getInterviewHistory() throws Exception {
    // given
    Long userId = 1L;
    List<Long> mockSessionIds = List.of(101L, 102L);

    given(customUserDetails.getId()).willReturn(userId);
    given(interviewService.getHistory(userId)).willReturn(mockSessionIds);

    // when & then
    mockMvc
        .perform(
            get("/v1/interviews/history")
                .with(user(customUserDetails))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.sessionIds[0]").value(101L))
        .andExpect(jsonPath("$.data.sessionIds[1]").value(102L));
  }

  // =========================================================================
  // [추가된 테스트] 리포트 조회 API 엔드포인트 검증
  // =========================================================================

  @Test
  @DisplayName("GET /v1/interviews/{sessionId}/report - 최종 리포트 조회 성공")
  void getInterviewReport_Success() throws Exception {
    // given
    Long sessionId = 1L;
    Long mockUserId = 1L; // 가짜 유저(userDetails)의 ID
    given(customUserDetails.getId()).willReturn(mockUserId);

    InterviewReportResponse mockResponse =
        InterviewReportResponse.builder()
            .sessionId(sessionId)
            .totalScore(95)
            .feedback("매우 훌륭한 답변입니다.")
            .build();

    // 서비스가 mockResponse를 반환하도록 세팅

    when(interviewService.getInterviewReport(eq(sessionId), anyLong())).thenReturn(mockResponse);

    // when & then
    mockMvc
        .perform(
            get("/v1/interviews/{sessionId}/report", sessionId)
                .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk()) // HTTP 200 확인
        .andExpect(jsonPath("$.data.sessionId").value(sessionId))
        .andExpect(jsonPath("$.data.totalScore").value(95))
        .andExpect(jsonPath("$.data.feedback").value("매우 훌륭한 답변입니다."));
  }
}
