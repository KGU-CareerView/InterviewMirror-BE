package com.interviewmirror.user.domain.interview.controller;

import com.interviewmirror.domain.interview.controller.InterviewController;
import com.interviewmirror.domain.interview.service.InterviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 💡 SecurityAutoConfiguration 제외
@WebMvcTest(controllers = InterviewController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewService interviewService;

    private static final String BASE_URL = "/api/v1/interviews";

    @Test
    @DisplayName("면접 결과 조회 API [GET] - 성공 시 200 반환")
    void getResultApiTest() throws Exception {
        Long sessionId = 100L;
        String fakeResult = "종합 피드백: 지원자의 답변이 훌륭합니다.";

        given(interviewService.getInterviewResult(sessionId)).willReturn(fakeResult);

        mockMvc.perform(get(BASE_URL + "/" + sessionId + "/result")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(fakeResult))
                .andDo(print());
    }

    @Test
    @DisplayName("사용자 과거 기록 조회 API [GET] - 성공 시 200 반환")
    void getHistoryApiTest() throws Exception {
        Long userId = 1L;
        List<Long> fakeHistory = List.of(100L, 101L, 102L);

        given(interviewService.getHistory(userId)).willReturn(fakeHistory);

        mockMvc.perform(get(BASE_URL + "/history")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionIds[0]").value(100L))
                .andExpect(jsonPath("$.sessionIds[1]").value(101L))
                .andDo(print());
    }
}