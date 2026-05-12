package com.interviewmirror.user.domain.interview.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.auth.jwt.JwtAuthenticationFilter;
import com.interviewmirror.auth.jwt.JwtTokenProvider;
import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.auth.service.AuthService;
import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.RabbitMQProducer;
import com.interviewmirror.config.S3Service;
import com.interviewmirror.config.SecurityConfig;
import com.interviewmirror.interview.controller.SessionController;
import com.interviewmirror.interview.service.InterviewPreparationService;
import com.interviewmirror.interview.service.InterviewService;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.user.entity.User;
import com.interviewmirror.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 🚨 핵심 수정: exclude를 지우고, 실제 시큐리티 설정을 가져옵니다 (@Import)
@WebMvcTest(SessionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SessionControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private SessionService sessionService;
  @MockitoBean private RedisSessionService redisSessionService;
  @MockitoBean private S3Service s3Service;
  @MockitoBean private AiGrpcClient aiGrpcClient;
  @MockitoBean private SimpMessagingTemplate messagingTemplate;
  @MockitoBean private InterviewService interviewService;
  @MockitoBean private InterviewPreparationService interviewPreparationService;
  @MockitoBean private RabbitMQProducer rabbitMQProducer;
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private AuthService authService;

  private static final String BASE_URL = "/api/v1/sessions";

  // 가짜 유저 데이터
  private CustomUserDetails testUserDetails;

  @BeforeEach
  void setUp() {
    User testUser =
        User.builder()
            .id(1L)
            .email("test@example.com")
            .name("TestUser")
            .passwordHash("hashedPwd")
            .build();
    testUserDetails = new CustomUserDetails(testUser);
  }

  @Test
  @DisplayName("세션 생성 API [POST] - 성공 시 201 상태와 세션 정보 반환")
  void createSessionApiTest() throws Exception {
    Long fakeSessionId = 100L;

    // sessionService가 1L(testUserDetails의 ID)을 받을 때 100L을 반환하도록 설정
    given(sessionService.createSession(1L)).willReturn(fakeSessionId);

    mockMvc
        .perform(
            post(BASE_URL)
                // 💡 이제 시큐리티가 켜져 있으므로 아래 구문이 완벽하게 작동합니다! (NPE 해결)
                .with(user(testUserDetails))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.sessionId").value(fakeSessionId))
        .andExpect(jsonPath("$.data.sessionState").value("INIT"))
        .andDo(print());
  }

  @Test
  @WithMockUser
  @DisplayName("상태 변경 API [PATCH] - 성공 시 200 반환")
  void updateSessionStatusApiTest() throws Exception {
    String requestJson = "{\"status\": \"START\"}";

    mockMvc
        .perform(
            patch(BASE_URL + "/100/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.Result").value("SUCCESS"))
        .andDo(print());

    verify(sessionService).changeState(100L, "START");
  }

  @Test
  @WithMockUser
  @DisplayName("세션 상태 조회 API [GET] - 성공 시 200 반환")
  void getSessionStateApiTest() throws Exception {
    given(redisSessionService.getSessionState(100L)).willReturn("START");

    mockMvc
        .perform(get(BASE_URL + "/100").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionState").value("START"))
        .andDo(print());
  }

  @Test
  @WithMockUser
  @DisplayName("S3 Presigned URL 발급 API [POST] - 성공 시 URL 반환")
  void getPresignedUrlApiTest() throws Exception {
    String requestJson = "{\"fileType\": \"video/mp4\", \"fileName\": \"my_interview.mp4\"}";
    String fakePresignedUrl = "https://s3.aws.com/fake-bucket/mock-url-12345";

    given(s3Service.generatePresignedUrl(eq(100L), anyString())).willReturn(fakePresignedUrl);

    mockMvc
        .perform(
            post(BASE_URL + "/100/presigned")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.presignedUrl").value(fakePresignedUrl))
        .andDo(print());
  }

  @Test
  @WithMockUser
  @DisplayName("미디어 URL DB 저장 API [POST] - 성공 시 200 반환")
  void saveMediaUrlApiTest() throws Exception {
    String videoUrl = "https://s3.aws.com/my-video.mp4";
    String requestJson = "{\"contentUrl\": \"" + videoUrl + "\"}";

    mockMvc
        .perform(
            post(BASE_URL + "/100/save/video")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.Result").value("SUCCESS"))
        .andDo(print());

    verify(sessionService, times(1)).saveVideoUrl(100L, videoUrl);
  }

  @Test
  @WithMockUser
  @DisplayName("답변 제출 API [POST] - 성공 시 202 ACCEPTED 반환")
  void submitAnswerApiTest() throws Exception {
    String requestJson = "{\"answer\": \"제 답변은 이렇습니다.\"}";

    mockMvc
        .perform(
            post(BASE_URL + "/100/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.Result").value("ok"))
        .andDo(print());

    verify(sessionService).processAnswerAndGenerateQuestion(100L, "제 답변은 이렇습니다.");
  }

  @Test
  @WithMockUser
  @DisplayName("감정 분석 API [POST] - 성공 시 WebSocket 전송 및 200 반환")
  void analyzeEmotionApiTest() throws Exception {
    String requestJson = "{\"data\": \"안면_특징점_데이터_123\"}";
    given(aiGrpcClient.analyzeEmotion(100L, "안면_특징점_데이터_123")).willReturn("SMILE");

    mockMvc
        .perform(
            post(BASE_URL + "/100/emotion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.message").value("전달완료"))
        .andExpect(jsonPath("$.data.result").value("SMILE"))
        .andDo(print());
  }
}
