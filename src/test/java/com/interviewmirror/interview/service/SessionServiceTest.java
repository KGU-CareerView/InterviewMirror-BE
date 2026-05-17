package com.interviewmirror.interview.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.infrastructure.RabbitMQProducer;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import com.interviewmirror.realtime.service.RealtimeMessagePublisher;
import com.interviewmirror.realtime.service.RealtimeQuestionGenerationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {
  @BeforeEach
  void setUp() {
    // ✅ 비어있는 테스트 환경에 가짜 트랜잭션 동기화를 켜줍니다.
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.initSynchronization();
    }
  }

  @AfterEach
  void tearDown() {
    // ✅ clear()만 호출해도 동기화 리소스가 깔끔하게 정리됩니다.
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clear();
    }
  }

  @InjectMocks private SessionService sessionService;

  @Mock private InterviewResultRepository resultRepository;

  @Mock private InterviewDetailRepository detailRepository;

  @Mock private RedisSessionService redisSessionService;

  @Mock private RealtimeQuestionGenerationService questionGenerationService;

  @Mock private RealtimeMessagePublisher realtimeMessagePublisher;

  @Mock private RabbitMQProducer rabbitMQProducer;

  // 실제 동작하는 ObjectMapper를 Spy로 주입
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("면접 세션 생성 테스트 - DB 저장 후 ID 반환 및 Redis 상태 업데이트 검증")
  void createSessionTest() {
    // given
    Long userId = 1L;
    InterviewResult mockResult =
        InterviewResult.builder()
            .sessionId(100L)
            .userId(userId)
            .sessionState(InterviewSessionState.READY.name())
            .createTime(LocalDateTime.now())
            .build();

    given(resultRepository.save(any(InterviewResult.class))).willReturn(mockResult);

    // when
    Long sessionId = sessionService.createSession(userId);

    // then
    verify(redisSessionService).updateSessionState(100L, InterviewSessionState.READY.name());
    assert sessionId == 100L;
  }

  @Test
  @DisplayName("면접 종료 테스트 (ENDED) - afterCommit 콜백 실행 검증")
  void changeStateEndTest() {
    // given
    Long sessionId = 1L;
    Long userId = 1L;

    InterviewResult mockResult =
        InterviewResult.builder()
            .sessionId(sessionId)
            .userId(userId)
            .sessionState(InterviewSessionState.IN_PROGRESS.name())
            .build();

    given(resultRepository.findById(sessionId)).willReturn(Optional.of(mockResult));

    String validJson = "{\"quizID\":1,\"question\":\"Q\",\"answer\":\"A\"}";
    given(redisSessionService.getQaList(sessionId)).willReturn(List.of(validJson));

    // when
    sessionService.changeState(sessionId, userId, InterviewSessionState.ENDED.name());

    List<TransactionSynchronization> synchronizations =
        TransactionSynchronizationManager.getSynchronizations();
    for (TransactionSynchronization synchronization : synchronizations) {
      synchronization.afterCommit(); // 이 코드가 실행되어야 verify가 통과됩니다.
    }

    // then
    verify(redisSessionService).updateSessionState(sessionId, InterviewSessionState.ENDED.name());
    verify(detailRepository, times(1)).save(any(InterviewDetail.class));
    verify(redisSessionService).clearQaList(sessionId);
    verify(rabbitMQProducer).sendReportRequest(sessionId);
  }

  @Test
  @DisplayName("답변 처리 후 꼬리 질문 생성을 realtime 서비스에 위임한다.")
  void processAnswerAndGenerateQuestionTest() throws Exception {
    // given
    Long sessionId = 1L;
    String answer = "이것은 답변입니다.";

    given(redisSessionService.getLastQuestion(sessionId)).willReturn("이전 질문입니다.");

    // when
    String emotionResult = "HAPPY";
    Integer responseTimeSeconds = 15;

    sessionService.processAnswerAndGenerateQuestion(
        sessionId, answer, emotionResult, responseTimeSeconds);

    // then
    verify(redisSessionService).addQaToRedis(eq(sessionId), anyString());
    verify(questionGenerationService).generateFollowUpQuestion(sessionId, "이전 질문입니다.", answer);
  }
}
