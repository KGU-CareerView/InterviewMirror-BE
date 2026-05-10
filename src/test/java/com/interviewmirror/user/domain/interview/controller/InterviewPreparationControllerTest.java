package com.interviewmirror.user.domain.interview.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.domain.interview.controller.InterviewPreparationController;
import com.interviewmirror.domain.interview.dto.AnswerTipRequest;
import com.interviewmirror.domain.interview.dto.InterviewSettingRequest;
import com.interviewmirror.domain.interview.dto.AnswerTipResponse;
import com.interviewmirror.domain.interview.entity.InterviewSetting;
import com.interviewmirror.domain.interview.service.InterviewPreparationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(
        value = InterviewPreparationController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class InterviewPreparationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InterviewPreparationService preparationService;

    @Test
    @DisplayName("면접 사전 설정을 저장하면 201 상태와 세션 ID를 응답한다.")
    void saveInterviewSetting_Success() throws Exception {
        // given
        InterviewSettingRequest request = new InterviewSettingRequest(
                1L, "BACKEND", "TECH", "NORMAL", 5, 120, "자소서입니다."
        );
        given(preparationService.saveSetting(any(InterviewSettingRequest.class))).willReturn(100L);

        // when & then
        mockMvc.perform(post("/api/v1/preparation/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settingId").value(100L))
                .andExpect(jsonPath("$.message").value("면접 설정이 성공적으로 저장되었습니다."))
                .andDo(print());
    }

    @Test
    @DisplayName("자소서와 질문을 보내면 생성된 답변 팁을 반환한다.")
    void generateAnswerTip_Success() throws Exception {
        // given
        AnswerTipRequest request = new AnswerTipRequest("JPA N+1 문제는?", "프로젝트에서 N+1 문제 해결함");
        AnswerTipResponse expectedResponse = AnswerTipResponse.builder().tip("Fetch Join을 활용해보세요.").build();

        given(preparationService.generateAnswerTip(any(AnswerTipRequest.class))).willReturn(expectedResponse);

        // when & then
        mockMvc.perform(post("/api/v1/preparation/tips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tip").value("Fetch Join을 활용해보세요."))
                .andDo(print());
    }

    @Test
    @DisplayName("가장 최근의 설정을 불러온다.")
    void getLatestSetting_Success() throws Exception {
        // given
        InterviewSetting setting = InterviewSetting.builder()
                .userId(1L)
                .category("BACKEND")
                .build();

        given(preparationService.getLatestSetting(anyLong())).willReturn(setting);

        // when & then
        mockMvc.perform(get("/api/v1/preparation/settings/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BACKEND"))
                .andDo(print());
    }
}