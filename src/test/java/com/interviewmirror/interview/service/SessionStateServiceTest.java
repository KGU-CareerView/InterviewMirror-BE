package com.interviewmirror.interview.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class SessionStateServiceTest {

  @InjectMocks private SessionStateService sessionStateService;

  @Mock private InterviewResultRepository resultRepository;

  @Mock private RedisSessionService redisSessionService;

  @Mock private FinalReportService finalReportService;

  @BeforeEach
  void setUp() {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.initSynchronization();
    }
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clear();
    }
  }

  @Test
  @DisplayName("면접 종료 상태 전이 시 Redis 상태를 갱신하고 최종 리포트 생성을 요청한다.")
  void changeStateEndTest() {
    Long sessionId = 1L;
    Long userId = 1L;

    InterviewResult mockResult =
        InterviewResult.builder()
            .sessionId(sessionId)
            .userId(userId)
            .sessionState(InterviewSessionState.IN_PROGRESS.name())
            .build();

    given(resultRepository.findByIdForUpdate(sessionId)).willReturn(Optional.of(mockResult));

    sessionStateService.changeState(sessionId, userId, InterviewSessionState.ENDED.name());

    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCommit();
    }

    verify(redisSessionService).updateSessionState(sessionId, InterviewSessionState.ENDED.name());
    verify(finalReportService).requestFinalReport(sessionId);
  }
}
