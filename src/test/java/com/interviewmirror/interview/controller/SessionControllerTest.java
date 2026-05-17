package com.interviewmirror.interview.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.auth.jwt.JwtTokenProvider;
import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.auth.service.AuthService;
import com.interviewmirror.infrastructure.RabbitMQProducer;
import com.interviewmirror.infrastructure.S3Service;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.dto.InterviewSettingResponse;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.service.InterviewPreparationService;
import com.interviewmirror.interview.service.InterviewService;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.user.entity.User;
import com.interviewmirror.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SessionControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private SessionService sessionService;
  @MockitoBean private RedisSessionService redisSessionService;
  @MockitoBean private S3Service s3Service;
  @MockitoBean private InterviewService interviewService;
  @MockitoBean private InterviewPreparationService interviewPreparationService;
  @MockitoBean private RabbitMQProducer rabbitMQProducer;
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private AuthService authService;
  @MockitoBean private CustomUserDetails customUserDetails;

  private static final String BASE_URL = "/v1/sessions";
  private CustomUserDetails testUserDetails;
  private final Long USER_ID = 1L;
  private final Long SESSION_ID = 100L;

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

  @BeforeEach
  void setUp() {
    // passwordHash 등 필드명 불일치 에러 방지를 위해 직접 생성
    User testUser = new User();
    testUser.setId(USER_ID);
    testUser.setEmail("test@example.com");
    testUserDetails = new CustomUserDetails(testUser);
    given(customUserDetails.getId()).willReturn(USER_ID);
  }

  @Test
  @DisplayName("세션 생성 API [POST] - 성공 시 201 반환")
  void createSessionApiTest() throws Exception {
    given(sessionService.createSession(USER_ID)).willReturn(SESSION_ID);

    mockMvc
        .perform(
            post(BASE_URL)
                .with(user(testUserDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID))
        .andExpect(jsonPath("$.data.sessionState").value(InterviewSessionState.READY.name()))
        .andDo(print());
  }

  @Test
  @DisplayName("면접 시작 API [POST] - 성공 시 202 반환")
  void startInterviewApiTest() throws Exception {
    InterviewSettingRequest request =
        new InterviewSettingRequest("BACKEND", "TECH", "NORMAL", 5, 30, "Spring Boot 경험...");
    given(interviewPreparationService.saveSetting(eq(SESSION_ID), eq(USER_ID), any()))
        .willReturn(InterviewSettingResponse.builder().settingId(10L).build());

    mockMvc
        .perform(
            post(BASE_URL + "/{sessionID}/start", SESSION_ID)
                .with(user(testUserDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.settingId").value(10L))
        .andDo(print());

    verify(interviewPreparationService).saveSetting(eq(SESSION_ID), eq(USER_ID), any());
  }

  @Test
  @DisplayName("상태 변경 API [PATCH] - 성공 시 200 반환")
  void updateSessionStatusApiTest() throws Exception {
    String requestJson = "{\"status\": \"PAUSED\"}";

    mockMvc
        .perform(
            patch(BASE_URL + "/{sessionID}/status", SESSION_ID)
                .with(user(testUserDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.Result").value("SUCCESS"))
        .andDo(print());

    verify(sessionService)
        .changeState(eq(SESSION_ID), eq(USER_ID), eq(InterviewSessionState.PAUSED.name()));
  }

  @Test
  @DisplayName("세션 상태 조회 API [GET] - 성공 시 200 반환")
  void getSessionStateApiTest() throws Exception {
    given(redisSessionService.getSessionState(SESSION_ID))
        .willReturn(InterviewSessionState.IN_PROGRESS.name());

    mockMvc
        .perform(get(BASE_URL + "/{sessionID}", SESSION_ID).with(user(testUserDetails)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionState").value(InterviewSessionState.IN_PROGRESS.name()))
        .andDo(print());
  }

  @Test
  @DisplayName("미디어 URL DB 저장 API [POST] - 성공 시 200 반환")
  void saveMediaUrlApiTest() throws Exception {
    String videoUrl = "http://example.com/video.mp4";
    String requestJson = "{\"type\": \"video\", \"contentUrl\": \"" + videoUrl + "\"}";

    mockMvc
        .perform(
            post(BASE_URL + "/{sessionID}/save", SESSION_ID)
                .with(user(testUserDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.Result").value("SUCCESS"))
        .andDo(print());

    verify(sessionService).saveVideoUrl(eq(SESSION_ID), eq(USER_ID), eq(videoUrl));
  }
}
