package com.interviewmirror.user.domain.interview.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import com.interviewmirror.interview.service.InterviewService;
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

  @InjectMocks private InterviewService interviewService;

  @Test
  @DisplayName("면접 결과 조회 성공 - DB에 세션이 존재함 (True 분기)")
  void getResult_Success_WhenSessionExists() {
    // given
    Long sessionId = 1L;
    InterviewResult mockResult =
        InterviewResult.builder().sessionId(sessionId).sessionState("END").build();

    given(interviewResultRepository.findById(sessionId)).willReturn(Optional.of(mockResult));

    // when (실제 반환 타입인 String으로 받기)
    String response = interviewService.getInterviewResult(sessionId);

    // then
    assertNotNull(response);
  }

  @Test
  @DisplayName("면접 결과 조회 실패 - DB에 세션이 존재하지 않음 (False 분기)")
  void getResult_Fail_WhenSessionNotFound() {
    // given
    Long sessionId = 999L;
    given(interviewResultRepository.findById(sessionId)).willReturn(Optional.empty());

    // when & then
    InterviewException exception =
        assertThrows(
            InterviewException.class,
            () -> {
              interviewService.getInterviewResult(sessionId);
            });

    assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
  }

  @Test
  @DisplayName("과거 면접 기록 조회 성공 - 기록이 1개 이상 존재함 (True 분기)")
  void getHistory_Success_WithHistory() {
    // given
    Long userId = 1L;
    InterviewResult result1 = InterviewResult.builder().sessionId(101L).build();
    InterviewResult result2 = InterviewResult.builder().sessionId(102L).build();

    given(interviewResultRepository.findByUserId(userId)).willReturn(List.of(result1, result2));

    // when (실제 반환 타입인 List<Long>으로 받기)
    List<Long> response = interviewService.getHistory(userId);

    // then
    assertNotNull(response);
    assertEquals(2, response.size());
    assertTrue(response.containsAll(List.of(101L, 102L)));
  }

  @Test
  @DisplayName("과거 면접 기록 조회 성공 - 기록이 하나도 없음 (False 분기)")
  void getHistory_Success_EmptyList() {
    // given
    Long userId = 1L;
    given(interviewResultRepository.findByUserId(userId)).willReturn(Collections.emptyList());

    // when (실제 반환 타입인 List<Long>으로 받기)
    List<Long> response = interviewService.getHistory(userId);

    // then
    assertNotNull(response);
    assertTrue(response.isEmpty()); // 빈 리스트 반환 검증
  }
}
