package com.interviewmirror.user.domain.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.interviewmirror.infrastructure.AiGrpcClient;
import com.interviewmirror.interview.dto.AnswerTipRequest;
import com.interviewmirror.interview.dto.AnswerTipResponse;
import com.interviewmirror.interview.dto.InterviewSettingDetailResponse;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSetting;
import com.interviewmirror.interview.repository.InterviewSettingRepository;
import com.interviewmirror.interview.service.InterviewPreparationService;
import com.interviewmirror.interview.service.SessionService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewPreparationServiceTest {

  @Mock private InterviewSettingRepository settingRepository;

  @Mock private AiGrpcClient aiGrpcClient;

  @Mock private SessionService sessionService; // 공통 검증 로직을 위한 세션 서비스 모킹

  @InjectMocks private InterviewPreparationService preparationService;

  private final Long SESSION_ID = 123L;
  private final Long USER_ID = 1L;

  @Test
  @DisplayName("면접 사전 설정을 성공적으로 저장하고 AI 서버에 질문 생성을 요청한다.")
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
    verify(settingRepository, times(1)).save(any(InterviewSetting.class));
    verify(aiGrpcClient, times(1))
        .requestInitialQuestions("BACKEND", "TECH", "NORMAL", 5, "Spring Boot 경험...");
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

  @Test
  @DisplayName("답변 팁 생성 요청 시 AI 서버로부터 응답을 받아 반환한다.")
  void generateAnswerTip_Success() {
    // given
    AnswerTipRequest request = new AnswerTipRequest("질문입니다", "이력서입니다");
    given(aiGrpcClient.requestTipGeneration("질문입니다", "이력서입니다")).willReturn("이러이러하게 답변하세요.");

    // when
    AnswerTipResponse response = preparationService.generateAnswerTip(request);

    // then
    assertEquals("이러이러하게 답변하세요.", response.getTip());
    verify(aiGrpcClient, times(1)).requestTipGeneration("질문입니다", "이력서입니다");
  }
}
