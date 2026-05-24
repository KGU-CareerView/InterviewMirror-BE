package com.interviewmirror.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.grpc.proto.FinalReportResponse;
import com.interviewmirror.interview.dto.InterviewReportResponse;
import com.interviewmirror.interview.dto.InterviewResultResponse;
import com.interviewmirror.interview.entity.InterviewReport;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewReportRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InterviewServiceTest {

  @Mock private InterviewResultRepository interviewResultRepository;

  @Mock private InterviewReportRepository reportRepository;

  @Mock private SessionService sessionService;

  @Mock private AudioScoreService audioScoreService;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private InterviewService interviewService;

  @Test
  @DisplayName("면접 결과 조회 성공 - DB에 세션이 존재함")
  void getResult_Success_WhenSessionExists() {
    Long sessionId = 1L;
    Long userId = 1L;
    InterviewResult mockResult =
        InterviewResult.builder()
            .sessionId(sessionId)
            .userId(userId)
            .sessionState(InterviewSessionState.ENDED.name())
            .emotionGraph("{\"happy\": 0.8}")
            .details(Collections.emptyList())
            .build();

    given(sessionService.getValidatedSession(sessionId, userId)).willReturn(mockResult);

    InterviewResultResponse response = interviewService.getInterviewResult(sessionId, userId);

    assertNotNull(response);
    assertEquals("{\"happy\": 0.8}", response.getEmotionGraph());
    verify(sessionService).getValidatedSession(sessionId, userId);
  }

  @Test
  @DisplayName("면접 결과 조회 실패 - 세션 검증 실패(IDOR 등)")
  void getResult_Fail_WhenSessionInvalid() {
    Long sessionId = 999L;
    Long userId = 1L;

    given(sessionService.getValidatedSession(sessionId, userId))
        .willThrow(new InterviewException(ErrorCode.SESSION_EXPIRED));

    InterviewException exception =
        assertThrows(
            InterviewException.class, () -> interviewService.getInterviewResult(sessionId, userId));

    assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
  }

  @Test
  @DisplayName("과거 면접 기록 조회 성공 - 기록이 1개 이상 존재함")
  void getHistory_Success_WithHistory() {
    Long userId = 1L;
    InterviewResult result1 = InterviewResult.builder().sessionId(101L).build();
    InterviewResult result2 = InterviewResult.builder().sessionId(102L).build();

    given(interviewResultRepository.findByUserId(userId)).willReturn(List.of(result1, result2));

    List<Long> response = interviewService.getHistory(userId);

    assertNotNull(response);
    assertEquals(2, response.size());
    assertTrue(response.containsAll(List.of(101L, 102L)));
    verify(interviewResultRepository).findByUserId(userId);
  }

  @Test
  @DisplayName("과거 면접 기록 조회 성공 - 기록이 하나도 없음")
  void getHistory_Success_EmptyList() {
    Long userId = 1L;
    given(interviewResultRepository.findByUserId(userId)).willReturn(Collections.emptyList());

    List<Long> response = interviewService.getHistory(userId);

    assertNotNull(response);
    assertTrue(response.isEmpty());
    verify(interviewResultRepository).findByUserId(userId);
  }

  // =========================================================================
  // 리포트 저장 (gRPC 응답)
  // =========================================================================

  @Test
  @DisplayName("리포트 저장 성공 - AI gRPC 응답 수신 시 DB에 저장된다")
  void saveReportFromGrpc_Success() {
    Long sessionId = 1L;
    FinalReportResponse grpcResponse =
        FinalReportResponse.newBuilder()
            .setSessionId(String.valueOf(sessionId))
            .setOverallScore(85.0)
            .setFinalAdvice("좋은 피드백")
            .build();
    InterviewResult result =
        InterviewResult.builder().sessionId(sessionId).details(Collections.emptyList()).build();

    when(reportRepository.existsById(sessionId)).thenReturn(false);
    when(interviewResultRepository.findById(sessionId)).thenReturn(Optional.of(result));
    when(audioScoreService.calculateSessionScore(result.getDetails())).thenReturn(90);
    when(audioScoreService.mergeAudioAnalysis(any(), any(), anyInt()))
        .thenReturn("{\"audio\":{\"overallScore\":90,\"scoredQuestionCount\":0}}");

    interviewService.saveReportFromGrpc(sessionId, grpcResponse);

    verify(reportRepository, times(1)).save(any(InterviewReport.class));
  }

  @Test
  @DisplayName("리포트 저장 무시 - 이미 저장된 리포트(중복 수신)는 무시된다")
  void saveReportFromGrpc_AlreadyExists_Ignored() {
    Long sessionId = 1L;
    FinalReportResponse grpcResponse =
        FinalReportResponse.newBuilder().setSessionId(String.valueOf(sessionId)).build();

    when(reportRepository.existsById(sessionId)).thenReturn(true);

    interviewService.saveReportFromGrpc(sessionId, grpcResponse);

    verify(interviewResultRepository, never()).findById(anyLong());
    verify(reportRepository, never()).save(any(InterviewReport.class));
  }

  @Test
  @DisplayName("리포트 저장 무시 - 세션이 이미 삭제된 경우 에러 없이 무시된다")
  void saveReportFromGrpc_SessionNotFound_Ignored() {
    Long sessionId = 1L;
    FinalReportResponse grpcResponse =
        FinalReportResponse.newBuilder().setSessionId(String.valueOf(sessionId)).build();

    when(reportRepository.existsById(sessionId)).thenReturn(false);
    when(interviewResultRepository.findById(sessionId)).thenReturn(Optional.empty());

    interviewService.saveReportFromGrpc(sessionId, grpcResponse);

    verify(reportRepository, never()).save(any(InterviewReport.class));
  }

  // =========================================================================
  // 리포트 조회
  // =========================================================================

  @Test
  @DisplayName("리포트 조회 성공 - 내 세션이고 분석이 완료되었을 때 DTO를 반환한다")
  void getInterviewReport_Success() {
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

    InterviewReportResponse response = interviewService.getInterviewReport(sessionId, myUserId);

    assertThat(response.getSessionId()).isEqualTo(sessionId);
    assertThat(response.getTotalScore()).isEqualTo(90);
    assertThat(response.getVideoUrl()).isEqualTo("s3.url");
  }

  @Test
  @DisplayName("리포트 조회 실패 - 타인의 세션을 조회하려고 하면 예외가 발생한다 (IDOR 방어)")
  void getInterviewReport_UnauthorizedAccess_ThrowsException() {
    Long sessionId = 1L;
    Long myUserId = 100L;
    Long otherUserId = 200L;

    InterviewResult result =
        InterviewResult.builder().sessionId(sessionId).userId(otherUserId).build();

    when(interviewResultRepository.findById(sessionId)).thenReturn(Optional.of(result));

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> interviewService.getInterviewReport(sessionId, myUserId));

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
  }

  @Test
  @DisplayName("리포트 조회 실패 - 세션은 있지만 아직 AI 분석 중(Report 없음)이면 예외가 발생한다")
  void getInterviewReport_NotReady_ThrowsException() {
    Long sessionId = 1L;
    Long myUserId = 100L;
    InterviewResult result =
        InterviewResult.builder().sessionId(sessionId).userId(myUserId).build();

    when(interviewResultRepository.findById(sessionId)).thenReturn(Optional.of(result));
    when(reportRepository.findById(sessionId)).thenReturn(Optional.empty());

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> interviewService.getInterviewReport(sessionId, myUserId));

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REPORT_NOT_READY);
  }
}
