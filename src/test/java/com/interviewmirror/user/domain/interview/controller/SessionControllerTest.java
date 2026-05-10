package com.interviewmirror.user.domain.interview.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.S3Service;
import com.interviewmirror.domain.interview.controller.SessionController;
import com.interviewmirror.domain.interview.service.RedisSessionService;
import com.interviewmirror.domain.interview.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 💡 SecurityAutoConfiguration 제외 (401, 403 에러 방지)
@WebMvcTest(
    controllers = SessionController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class)
class SessionControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private SessionService sessionService;
  @MockitoBean private RedisSessionService redisSessionService;
  @MockitoBean private S3Service s3Service;
  @MockitoBean private AiGrpcClient aiGrpcClient;
  @MockitoBean private SimpMessagingTemplate messagingTemplate;

  private static final String BASE_URL = "/api/v1/user/1/sessions";

  @Test
  @DisplayName("세션 생성 API [POST] - 성공 시 201 상태와 세션 정보 반환")
  void createSessionApiTest() throws Exception {
    Long fakeSessionId = 100L;
    given(sessionService.createSession(anyLong())).willReturn(fakeSessionId);

    mockMvc
        .perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sessionId").value(fakeSessionId))
        .andExpect(jsonPath("$.sessionState").value("INIT"))
        .andDo(print());
  }

  @Test
  @DisplayName("상태 변경 API [PATCH] - 성공 시 200 반환")
  void updateSessionStatusApiTest() throws Exception {
    String requestJson = "{\"status\": \"START\"}";

    mockMvc
        .perform(
            patch(BASE_URL + "/100/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.Result").value("SUCCESS"))
        .andDo(print());

    verify(sessionService).changeState(100L, "START");
  }

  @Test
  @DisplayName("세션 상태 조회 API [GET] - 성공 시 200 반환")
  void getSessionStateApiTest() throws Exception {
    given(redisSessionService.getSessionState(100L)).willReturn("START");

    mockMvc
        .perform(get(BASE_URL + "/100").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionState").value("START"))
        .andDo(print());
  }

  @Test
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
        .andExpect(jsonPath("$.presignedUrl").value(fakePresignedUrl))
        .andDo(print());
  }

  @Test
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
        .andExpect(jsonPath("$.Result").value("SUCCESS"))
        .andDo(print());

    verify(sessionService, times(1)).saveVideoUrl(100L, videoUrl);
  }

  @Test
  @DisplayName("답변 제출 API [POST] - 성공 시 202 ACCEPTED 반환")
  void submitAnswerApiTest() throws Exception {
    String requestJson = "{\"answer\": \"제 답변은 이렇습니다.\"}";

    mockMvc
        .perform(
            post(BASE_URL + "/100/answer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.Result").value("ok"))
        .andDo(print());

    verify(sessionService).processAnswerAndGenerateQuestion(100L, "제 답변은 이렇습니다.");
  }

  @Test
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
        .andExpect(jsonPath("$.message").value("전달완료"))
        .andExpect(jsonPath("$.result").value("SMILE"))
        .andDo(print());
  }
}
