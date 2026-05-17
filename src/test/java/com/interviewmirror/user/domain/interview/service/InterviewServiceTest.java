package com.interviewmirror.user.domain.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.interview.dto.AiReportResponse;
import com.interviewmirror.interview.dto.InterviewReportResponse;
import com.interviewmirror.interview.dto.InterviewResultResponse;
import com.interviewmirror.interview.entity.InterviewReport;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.repository.InterviewReportRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import com.interviewmirror.interview.service.InterviewService;
import com.interviewmirror.interview.service.SessionService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InterviewServiceTest {

  @Mock private InterviewResultRepository interviewResultRepository;

  // 💡 추가됨: 리포트 저장을 위한 Mock 레포지토리
  @Mock private InterviewReportRepository reportRepository;

  @Mock private SessionService sessionService;

  @InjectMocks private InterviewService interviewService;

  @Test
  @DisplayName("면접 결과 조회 성공 - DB에 세션이 존재함")
  void getResult_Success_WhenSessionExists() {
    // given
    Long sessionId = 1L;
    Long userId = 1L;
    InterviewResult mockResult =
        InterviewResult.builder()
            .sessionId(sessionId)
            .userId(userId)
            .sessionState("END")
            .emotionGraph("{\"happy\": 0.8}") // 데이터가 있는 상태
            .details(
                Collections.emptyList()) // 💡 핵심: Service에서 .stream()을 호출하므로 Null 방지를 위해 빈 리스트 주입
            .build();

    given(sessionService.getValidatedSession(sessionId, userId)).willReturn(mockResult);

    // when
    InterviewResultResponse response = interviewService.getInterviewResult(sessionId, userId);

    // then
    assertNotNull(response);
    assertEquals("{\"happy\": 0.8}", response.getEmotionGraph());
    verify(sessionService).getValidatedSession(sessionId, userId);
  }

  @Test
  @DisplayName("면접 결과 조회 실패 - 세션 검증 실패(IDOR 등)")
  void getResult_Fail_WhenSessionInvalid() {
    // given
    Long sessionId = 999L;
    Long userId = 1L;

    given(sessionService.getValidatedSession(sessionId, userId))
        .willThrow(new InterviewException(ErrorCode.SESSION_EXPIRED));

    // when & then
    InterviewException exception =
        assertThrows(
            InterviewException.class,
            () -> {
              interviewService.getInterviewResult(sessionId, userId);
            });

    assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
  }

  @Test
  @DisplayName("과거 면접 기록 조회 성공 - 기록이 1개 이상 존재함")
  void getHistory_Success_WithHistory() {
    // given
    Long userId = 1L;
    InterviewResult result1 = InterviewResult.builder().sessionId(101L).build();
    InterviewResult result2 = InterviewResult.builder().sessionId(102L).build();

    given(interviewResultRepository.findByUserId(userId)).willReturn(List.of(result1, result2));

    // when
    List<Long> response = interviewService.getHistory(userId);

    // then
    assertNotNull(response);
    assertEquals(2, response.size());
    assertTrue(response.containsAll(List.of(101L, 102L)));
    verify(interviewResultRepository).findByUserId(userId);
  }

  @Test
  @DisplayName("과거 면접 기록 조회 성공 - 기록이 하나도 없음")
  void getHistory_Success_EmptyList() {
    // given
    Long userId = 1L;
    given(interviewResultRepository.findByUserId(userId)).willReturn(Collections.emptyList());

    // when
    List<Long> response = interviewService.getHistory(userId);

    // then
    assertNotNull(response);
    assertTrue(response.isEmpty());
    verify(interviewResultRepository).findByUserId(userId);
  }

  // =========================================================================
  // [추가된 테스트] 1. 면접 결과 리포트 저장 (RabbitMQ 수신)
  // =========================================================================

  @Test
  @DisplayName("리포트 저장 성공 - 정상적인 AI 응답 수신 시 DB에 저장된다")
  void saveInterviewReport_Success() {
    // given
    Long sessionId = 1L;
    AiReportResponse aiResponse = new AiReportResponse(sessionId, 85, "좋은 피드백", "강점", "약점", "{}");
    InterviewResult result = InterviewResult.builder().sessionId(sessionId).build();

    when(reportRepository.existsById(sessionId)).thenReturn(false);
    // 💡 수정됨: resultRepository -> interviewResultRepository로 이름 일치시킴
    when(interviewResultRepository.findById(sessionId)).thenReturn(Optional.of(result));

    // when
    interviewService.saveInterviewReport(aiResponse);

    // then
    verify(reportRepository, times(1)).save(any(InterviewReport.class));
  }

  @Test
  @DisplayName("리포트 저장 무시 - 이미 저장된 리포트(RabbitMQ 중복 수신)는 무시된다")
  void saveInterviewReport_AlreadyExists_Ignored() {
    // given
    Long sessionId = 1L;
    AiReportResponse aiResponse = new AiReportResponse(sessionId, 85, "좋은 피드백", "강점", "약점", "{}");

    when(reportRepository.existsById(sessionId)).thenReturn(true);

    // when
    interviewService.saveInterviewReport(aiResponse);

    // then
    verify(interviewResultRepository, never()).findById(anyLong());
    verify(reportRepository, never()).save(any(InterviewReport.class));
  }

  @Test
  @DisplayName("리포트 저장 무시 - 세션이 이미 삭제된 고아 메시지는 에러 없이 무시된다")
  void saveInterviewReport_SessionNotFound_Ignored() {
    // given
    Long sessionId = 1L;
    AiReportResponse aiResponse = new AiReportResponse(sessionId, 85, "좋은 피드백", "강점", "약점", "{}");

    when(reportRepository.existsById(sessionId)).thenReturn(false);
    when(interviewResultRepository.findById(sessionId)).thenReturn(Optional.empty());

    // when
    interviewService.saveInterviewReport(aiResponse);

    // then
    verify(reportRepository, never()).save(any(InterviewReport.class));
  }

  // =========================================================================
  // [추가된 테스트] 2. 면접 결과 리포트 조회 (프론트엔드 요청)
  // =========================================================================

  @Test
  @DisplayName("리포트 조회 성공 - 내 세션이고, 분석이 완료되었을 때 DTO를 반환한다")
  void getInterviewReport_Success() {
    // given
    Long sessionId = 1L;
    Long myUserId = 100L;

    InterviewResult result =
        InterviewResult.builder()
            .sessionId(sessionId)
            .userId(myUserId)
            .videoUrl("s3.url")
            .emotionGraph("[]")
            .build();
    InterviewReport report =
        InterviewReport.builder().interviewResult(result).totalScore(90).feedback("최고").build();

    when(interviewResultRepository.findById(sessionId)).thenReturn(Optional.of(result));
    when(reportRepository.findById(sessionId)).thenReturn(Optional.of(report));

    // when
    InterviewReportResponse response = interviewService.getInterviewReport(sessionId, myUserId);

    // then
    assertThat(response.getSessionId()).isEqualTo(sessionId);
    assertThat(response.getTotalScore()).isEqualTo(90);
    assertThat(response.getVideoUrl()).isEqualTo("s3.url");
  }

  @Test
  @DisplayName("리포트 조회 실패 - 타인의 세션을 조회하려고 하면 예외가 발생한다 (IDOR 방어)")
  void getInterviewReport_UnauthorizedAccess_ThrowsException() {
    // given
    Long sessionId = 1L;
    Long myUserId = 100L;
    Long otherUserId = 200L;

    InterviewResult result =
        InterviewResult.builder().sessionId(sessionId).userId(otherUserId).build();

    when(interviewResultRepository.findById(sessionId)).thenReturn(Optional.of(result));

    // when & then
    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> interviewService.getInterviewReport(sessionId, myUserId));

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
  }

  @Test
  @DisplayName("리포트 조회 실패 - 세션은 있지만 아직 AI 분석 중(Report 없음)이면 예외가 발생한다")
  void getInterviewReport_NotReady_ThrowsException() {
    // given
    Long sessionId = 1L;
    Long myUserId = 100L;
    InterviewResult result =
        InterviewResult.builder().sessionId(sessionId).userId(myUserId).build();

    when(interviewResultRepository.findById(sessionId)).thenReturn(Optional.of(result));
    when(reportRepository.findById(sessionId)).thenReturn(Optional.empty());

    // when & then
    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> interviewService.getInterviewReport(sessionId, myUserId));

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REPORT_NOT_READY);
  }
}
