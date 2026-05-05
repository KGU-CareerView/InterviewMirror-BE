package com.interviewmirror.user.domain.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.S3Service;
import com.interviewmirror.domain.interview.service.RedisSessionService;
import com.interviewmirror.domain.interview.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.interviewmirror.domain.interview.controller.SessionController;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(SessionController.class) // Controller 계층만 떼어내서 가볍게 테스트
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc; // 가상의 HTTP 요청을 보내는 객체

    @Autowired
    private ObjectMapper objectMapper;

    // Controller가 의존하는 서비스들을 Mock(가짜)으로 등록
    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private RedisSessionService redisSessionService;
    @MockitoBean
    private S3Service s3Service;
    @MockitoBean
    private AiGrpcClient aiGrpcClient;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("세션 생성 API [POST /sessions] - 성공 시 201 상태와 세션 정보 반환")
    void createSessionApiTest() throws Exception {
        // given: sessionService.createSession()을 호출하면 1L을 반환하도록 가짜 시나리오 설정
        Long fakeSessionId = 1L;
        given(sessionService.createSession(fakeSessionId)).willReturn(fakeSessionId);

        // when & then: POST /sessions 요청을 보내고 결과를 검증
        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()) // HTTP 201 상태인지 확인
                .andExpect(jsonPath("$.sessionId").value(fakeSessionId)) // JSON에 sessionId가 1인지 확인
                .andExpect(jsonPath("$.sessionState").value("INIT")) // JSON에 sessionState가 INIT인지 확인
                .andDo(print()); // 콘솔에 요청/응답 전체 로그 출력
    }

    @Test
    @DisplayName("상태 변경 API [PATCH /sessions/{id}/status] - 성공 시 200 반환")
    void updateSessionStatusApiTest() throws Exception {
        // given: 프론트에서 보낼 JSON 데이터 생성
        String requestJson = "{\"status\": \"START\"}";

        // when & then: PATCH 요청 쏘고 결과 확인
        mockMvc.perform(patch("/sessions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Result").value("SUCCESS"))
                .andDo(print());
    }

    @Test
    @DisplayName("세션 상태 조회 API [GET /sessions/{id}] - 성공 시 200 반환")
    void getSessionStateApiTest() throws Exception {
        // given: Redis에서 "START" 상태를 반환하도록 가짜 시나리오 설정
        given(redisSessionService.getSessionState(1L)).willReturn("START");

        // when & then
        mockMvc.perform(get("/sessions/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionState").value("START"))
                .andDo(print());
    }

    @Test
    @DisplayName("답변 제출 API [POST /sessions/{id}/answer] - 성공 시 202 ACCEPTED 반환")
    void submitAnswerApiTest() throws Exception {
        // given
        String requestJson = "{\"answer\": \"제 답변은 이렇습니다.\"}";

        // when & then: 비동기 작업이므로 202 상태가 떨어지는지 확인
        mockMvc.perform(post("/sessions/1/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted()) // 💡 HTTP 202
                .andExpect(jsonPath("$.Result").value("ok"))
                .andDo(print());
    }

    @Test
    @DisplayName("감정 분석 API [POST /sessions/{id}/emotion] - 성공 시 WebSocket 전송 및 200 반환")
    void analyzeEmotionApiTest() throws Exception {
        // given
        String requestJson = "{\"data\": \"안면_특징점_데이터_123\"}";
        // gRPC 가짜 응답 설정
        given(aiGrpcClient.analyzeEmotion(1L, "안면_특징점_데이터_123")).willReturn("SMILE");

        // when & then
        mockMvc.perform(post("/sessions/1/emotion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("전달완료"))
                .andExpect(jsonPath("$.result").value("SMILE"))
                .andDo(print());
    }

    @Test
    @DisplayName("S3 Presigned URL 발급 API [POST /sessions/{id}/presigned] - 성공 시 URL 반환")
    void getPresignedUrlApiTest() throws Exception {
        // given
        String requestJson = "{\"fileType\": \"video/mp4\", \"fileName\": \"my_interview.mp4\"}";
        String fakePresignedUrl = "https://s3.aws.com/fake-bucket/mock-url-12345";

        // 어떤 파일 타입이 들어오든 가짜 URL을 반환하도록 설정
        given(s3Service.generatePresignedUrl(eq(1L), anyString(), anyString())).willReturn(fakePresignedUrl);

        // when & then
        mockMvc.perform(post("/sessions/1/presigned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presignedUrl").value(fakePresignedUrl))
                .andDo(print());
    }

    @Test
    @DisplayName("미디어 URL DB 저장 API [POST /sessions/{id}/save/{mediaType}] - 성공 시 200 반환")
    void saveMediaUrlApiTest() throws Exception {
        // given
        String videoUrl = "https://s3.aws.com/my-video.mp4";
        String requestJson = "{\"contentUrl\": \"" + videoUrl + "\"}";

        // when & then
        mockMvc.perform(post("/sessions/1/save/video") // 💡 url 경로에 "video" 라고 명시
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Result").value("SUCCESS"))
                .andDo(print());

        // Controller가 Service의 saveVideoUrl 메서드를 정확히 호출했는지 검증
        verify(sessionService, times(1)).saveVideoUrl(1L, videoUrl);
    }
}