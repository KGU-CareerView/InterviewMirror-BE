package com.interviewmirror.user.domain.interview.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.interviewmirror.auth.jwt.JwtTokenProvider;
import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.auth.service.AuthService;
import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.RabbitMQProducer;
import com.interviewmirror.config.S3Service;
import com.interviewmirror.config.SecurityConfig;
import com.interviewmirror.interview.controller.InterviewController;
import com.interviewmirror.interview.service.InterviewPreparationService;
import com.interviewmirror.interview.service.InterviewService;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = InterviewController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}, // 보안 필터 제외
    excludeFilters = {
      @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    })
public class InterviewControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean // ✅ 이제 경고 없이 정상 작동합니다.
  private InterviewService interviewService;
  @MockitoBean private SessionService sessionService;
  @MockitoBean private InterviewPreparationService interviewPreparationService;
  @MockitoBean private RedisSessionService redisSessionService;
  @MockitoBean private S3Service s3Service;
  @MockitoBean private AiGrpcClient aiGrpcClient;
  @MockitoBean private RabbitMQProducer rabbitMQProducer;
  @MockitoBean private SimpMessagingTemplate messagingTemplate;
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private CustomUserDetails customUserDetails;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private AuthService authService;

  @Test
  @DisplayName("면접 결과 조회 API 성공 (200 OK)")
  void getInterviewResult() throws Exception {
    // given
    Long sessionId = 1L;
    // Service가 String을 반환하도록 수정
    given(interviewService.getInterviewResult(sessionId)).willReturn("AI 분석 결과 텍스트/JSON");

    // when & then
    mockMvc
        .perform(
            get("/api/v1/interviews/{sessionID}/result", sessionId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("과거 면접 기록 조회 API 성공 (200 OK)")
  void getInterviewHistory() throws Exception {
    // given
    Long userId = 1L;
    // Service가 List<Long>을 반환하도록 수정
    given(interviewService.getHistory(userId)).willReturn(List.of(101L, 102L));

    // when & then
    mockMvc
        .perform(
            get("/api/v1/interviews/history")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}
