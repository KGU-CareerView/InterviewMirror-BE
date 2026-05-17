package com.interviewmirror.interview.controller;

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
import com.interviewmirror.auth.jwt.JwtTokenProvider;
import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.auth.service.AuthService;
import com.interviewmirror.interview.controller.InterviewPreparationController;
import com.interviewmirror.interview.dto.InterviewSettingDetailResponse;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.service.InterviewPreparationService;
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

@WebMvcTest(controllers = InterviewPreparationController.class)
@AutoConfigureMockMvc(addFilters = false)
class InterviewPreparationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private InterviewPreparationService preparationService;

  // 💡 보안 필터 통과를 위해 필요한 Bean들 모킹
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private AuthService authService;
  @MockitoBean private CustomUserDetails customUserDetails;

  private CustomUserDetails mockUser;
  private final Long SESSION_ID = 123L;
  private final Long USER_ID = 1L;

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
    User user = new User();
    user.setId(USER_ID);
    mockUser = new CustomUserDetails(user);
    given(customUserDetails.getId()).willReturn(USER_ID);
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
