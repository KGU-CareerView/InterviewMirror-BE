package com.interviewmirror.user.domain.interview.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.domain.interview.dto.AnswerTipRequest;
import com.interviewmirror.domain.interview.dto.AnswerTipResponse;
import com.interviewmirror.domain.interview.dto.InterviewSettingRequest;
import com.interviewmirror.domain.interview.entity.InterviewSetting;
import com.interviewmirror.domain.interview.repository.InterviewSettingRepository;
import com.interviewmirror.domain.interview.service.InterviewPreparationService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
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

  @InjectMocks private InterviewPreparationService preparationService;

  @Test
  @DisplayName("면접 사전 설정을 저장하고 gRPC 초기 질문 생성을 비동기로 요청한다.")
  void saveSetting_Success() {
    // given
    InterviewSettingRequest request =
        new InterviewSettingRequest(1L, "BACKEND", "TECH", "NORMAL", 5, 120, "Spring Boot 경험...");

    InterviewSetting mockSavedSetting =
        InterviewSetting.builder()
            .userId(1L)
            .category("BACKEND")
            .interviewType("TECH")
            .difficulty("NORMAL")
            .questionCount(5)
            .timePerQuestion(120)
            .resumeContent("Spring Boot 경험...")
            .build();
    mockSavedSetting.setSettingId(100L); // 가정된 생성 ID (Setter가 있다고 가정)

    when(settingRepository.save(any(InterviewSetting.class))).thenReturn(mockSavedSetting);
    when(aiGrpcClient.requestInitialQuestions(any(), any(), any(), anyInt(), any()))
        .thenReturn(List.of("가상 질문 1", "가상 질문 2"));

    // when
    Long settingId = preparationService.saveSetting(request);

    // then
    assertEquals(100L, settingId);
    verify(settingRepository, times(1)).save(any(InterviewSetting.class));
    verify(aiGrpcClient, times(1))
        .requestInitialQuestions("BACKEND", "TECH", "NORMAL", 5, "Spring Boot 경험...");
  }

  @Test
  @DisplayName("가장 최근의 면접 설정을 성공적으로 조회한다.")
  void getLatestSetting_Success() {
    // given
    Long userId = 1L;
    InterviewSetting setting = InterviewSetting.builder().userId(userId).build();

    when(settingRepository.findTopByUserIdOrderBySettingIdDesc(userId))
        .thenReturn(Optional.of(setting));

    // when
    InterviewSetting result = preparationService.getLatestSetting(userId);

    // then
    assertNotNull(result);
    assertEquals(userId, result.getUserId());
  }

  @Test
  @DisplayName("면접 설정 내역이 없을 경우 EntityNotFoundException을 발생시킨다.")
  void getLatestSetting_ThrowsException() {
    // given
    Long userId = 999L;
    when(settingRepository.findTopByUserIdOrderBySettingIdDesc(userId))
        .thenReturn(Optional.empty());

    // when & then
    assertThrows(EntityNotFoundException.class, () -> preparationService.getLatestSetting(userId));
  }

  @Test
  @DisplayName("AI gRPC를 호출하여 성공적으로 답변 팁을 생성한다.")
  void generateAnswerTip_Success() {
    // given
    AnswerTipRequest request = new AnswerTipRequest("JPA의 장점은?", "자소서 내용...");
    when(aiGrpcClient.requestTipGeneration("JPA의 장점은?", "자소서 내용..."))
        .thenReturn("JPA는 객체지향적인 설계를 도와줍니다.");

    // when
    AnswerTipResponse response = preparationService.generateAnswerTip(request);

    // then
    assertNotNull(response);
    assertEquals("JPA는 객체지향적인 설계를 도와줍니다.", response.getTip());
    verify(aiGrpcClient, times(1)).requestTipGeneration(anyString(), anyString());
  }
}
