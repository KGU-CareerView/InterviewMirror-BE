package com.interviewmirror.user.domain.interview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.auth.jwt.JwtAuthenticationFilter;
import com.interviewmirror.auth.jwt.JwtTokenProvider;
import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.auth.service.AuthService;
import com.interviewmirror.config.SecurityConfig;
import com.interviewmirror.interview.controller.InterviewPreparationController;
import com.interviewmirror.interview.dto.InterviewSettingDetailResponse;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.service.InterviewPreparationService;
import com.interviewmirror.user.entity.User;
import com.interviewmirror.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InterviewPreparationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class}) // 💡 보안 설정 및 필터 추가
class InterviewPreparationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private InterviewPreparationService preparationService;

  // 💡 보안 필터 통과를 위해 필요한 Bean들 모킹
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private AuthService authService;

  private CustomUserDetails mockUser;
  private final Long SESSION_ID = 123L;
  private final Long USER_ID = 1L;

  @BeforeEach
  void setUp() {
    User user = new User();
    user.setId(USER_ID);
    mockUser = new CustomUserDetails(user);
  }

  @Test
  @DisplayName("면접 사전 설정 저장 API [POST] - 성공 시 201 상태와 세션 ID 반환")
  void saveInterviewSetting_Success() throws Exception {
    InterviewSettingRequest request =
        new InterviewSettingRequest("BACKEND", "TECH", "NORMAL", 5, 30, "Spring Boot 경험...");
    given(preparationService.saveSetting(eq(SESSION_ID), eq(USER_ID), any())).willReturn(100L);

    // 💡 경로 수정: settings/save/{sessionId}
    mockMvc
        .perform(
            post("/v1/preparation/settings/save/{sessionId}", SESSION_ID)
                .with(user(mockUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.settingId").value(100L));
  }

  @Test
  @DisplayName("특정 세션 면접 설정 조회 API [GET] - 성공 시 200 반환")
  void getSettingBySessionId_Success() throws Exception {
    // 1. 💡 엔티티 대신 DTO 객체를 생성합니다.
    InterviewSettingDetailResponse mockResponse =
        InterviewSettingDetailResponse.builder()
            .settingId(100L)
            .category("BACKEND")
            .interviewType("TECH")
            .difficulty("NORMAL")
            .questionCount(5)
            .build();

    // 서비스가 DTO를 반환하도록 Mock 세팅을 변경합니다.
    given(preparationService.getSettingBySessionId(SESSION_ID, USER_ID)).willReturn(mockResponse);

    // when & then
    mockMvc
        .perform(get("/v1/preparation/settings/{sessionId}", SESSION_ID).with(user(mockUser)))
        .andDo(print())
        .andExpect(status().isOk())
        // DTO 구조에 맞춰 JSON 경로를 검증합니다.
        .andExpect(jsonPath("$.data.category").value("BACKEND"))
        .andExpect(jsonPath("$.data.settingId").value(100L));
  }
}
