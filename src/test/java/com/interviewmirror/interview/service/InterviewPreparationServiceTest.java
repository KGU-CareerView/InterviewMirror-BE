package com.interviewmirror.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.interviewmirror.interview.dto.InterviewSettingDetailResponse;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSetting;
import com.interviewmirror.interview.repository.InterviewSettingRepository;
import com.interviewmirror.realtime.service.RealtimeQuestionGenerationService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewPreparationServiceTest {

  @Mock private InterviewSettingRepository settingRepository;

  @Mock private RealtimeQuestionGenerationService questionGenerationService;

  @Mock private SessionService sessionService; // 공통 검증 로직을 위한 세션 서비스 모킹

  @Mock private SessionStateService sessionStateService;

  @InjectMocks private InterviewPreparationService preparationService;

  private final Long SESSION_ID = 123L;
  private final Long USER_ID = 1L;

  @Test
  @DisplayName("면접 사전 설정을 저장하고 세션 시작 후 초기 질문 생성을 요청한다.")
  void saveSetting_Success() {
    // given
    InterviewSettingRequest request =
        new InterviewSettingRequest("BACKEND", "TECH", "NORMAL", 5, 30, "Spring Boot 경험...");

    InterviewResult mockSession =
        InterviewResult.builder().sessionId(SESSION_ID).userId(USER_ID).build();

    InterviewSetting mockSavedSetting =
        InterviewSetting.builder().interviewResult(mockSession).category("BACKEND").build();

    given(sessionService.getValidatedSession(SESSION_ID, USER_ID)).willReturn(mockSession);
    given(settingRepository.save(any(InterviewSetting.class))).willReturn(mockSavedSetting);

    // when
    preparationService.saveSetting(SESSION_ID, USER_ID, request);

    // then
    verify(sessionService, times(1)).getValidatedSession(SESSION_ID, USER_ID);
    verify(sessionStateService, times(1)).markPreparing(SESSION_ID, USER_ID);
    ArgumentCaptor<InterviewSetting> settingCaptor =
        ArgumentCaptor.forClass(InterviewSetting.class);
    verify(settingRepository, times(1)).save(settingCaptor.capture());
    assertEquals(USER_ID, settingCaptor.getValue().getUserId());
    verify(questionGenerationService, times(1))
        .generateInitialQuestions(SESSION_ID, USER_ID, request);
  }

  @Test
  @DisplayName("세션 ID로 면접 설정을 성공적으로 조회한다.")
  void getSettingBySessionId_Success() {
    // given
    InterviewResult mockSession =
        InterviewResult.builder().sessionId(SESSION_ID).userId(USER_ID).build();

    InterviewSetting mockSetting =
        InterviewSetting.builder()
            .settingId(100L) // settingId 추가
            .interviewResult(mockSession)
            .category("BACKEND")
            .interviewType("TECH")
            .difficulty("NORMAL")
            .questionCount(5)
            .build();

    given(sessionService.getValidatedSession(SESSION_ID, USER_ID)).willReturn(mockSession);
    given(settingRepository.findByInterviewResult_SessionId(SESSION_ID))
        .willReturn(Optional.of(mockSetting));

    // when
    // 💡 [핵심 수정] 타입을 InterviewSettingDetailResponse 로 변경합니다.
    InterviewSettingDetailResponse result =
        preparationService.getSettingBySessionId(SESSION_ID, USER_ID);

    // then
    assertNotNull(result);
    assertEquals("BACKEND", result.getCategory());
    assertEquals(100L, result.getSettingId()); // DTO에 포함된 ID 검증
    verify(sessionService, times(1)).getValidatedSession(SESSION_ID, USER_ID);
  }
}
