package com.interviewmirror.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.infrastructure.RabbitMQProducer;
import com.interviewmirror.interview.dto.SessionStateResponse;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStateService {

  private static final TypeReference<Map<String, Object>> QA_MAP_TYPE = new TypeReference<>() {};

  private final InterviewResultRepository resultRepository;
  private final InterviewDetailRepository detailRepository;
  private final RedisSessionService redisSessionService;
  private final RabbitMQProducer rabbitMQProducer;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public SessionStateResponse getSessionState(Long sessionId) {
    String state = redisSessionService.getSessionState(sessionId);
    if (state == null) {
      throw new InterviewException(ErrorCode.SESSION_EXPIRED);
    }

    return SessionStateResponse.builder().sessionState(state).build();
  }

  @Transactional
  public void changeState(Long sessionId, Long userId, String state) {
    InterviewSessionState nextState = InterviewSessionState.from(state);
    validateUserRequestedState(nextState);

    InterviewResult result = getValidatedSession(sessionId, userId);
    validateTransition(result, nextState);
    applyStateChange(sessionId, result, nextState);
  }

  @Transactional
  public void markPreparing(Long sessionId, Long userId) {
    InterviewResult result = getValidatedSession(sessionId, userId);
    validateTransition(result, InterviewSessionState.PREPARING);
    applyStateChange(sessionId, result, InterviewSessionState.PREPARING);
  }

  @Transactional
  public void changeStateBySystem(Long sessionId, InterviewSessionState nextState) {
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_NOT_FOUND));
    validateTransition(result, nextState);
    applyStateChange(sessionId, result, nextState);
  }

  @Transactional
  public void autoPauseSession(Long sessionId) {
    InterviewResult result = resultRepository.findById(sessionId).orElse(null);
    if (result == null) {
      return;
    }

    InterviewSessionState currentState = InterviewSessionState.from(result.getSessionState());
    if (currentState == InterviewSessionState.READY
        || currentState == InterviewSessionState.PAUSED
        || currentState == InterviewSessionState.ENDED) {
      return;
    }

    log.info("[SessionID: {}] 비정상 종료 감지. 시스템이 자동으로 PAUSED 상태로 전환합니다.", sessionId);
    validateTransition(result, InterviewSessionState.PAUSED);
    applyStateChange(sessionId, result, InterviewSessionState.PAUSED);
  }

  private void validateUserRequestedState(InterviewSessionState nextState) {
    if (nextState == InterviewSessionState.READY || nextState == InterviewSessionState.PREPARING) {
      throw new InterviewException(ErrorCode.VALIDATION_ERROR);
    }
  }

  private void validateTransition(InterviewResult result, InterviewSessionState nextState) {
    InterviewSessionState currentState = InterviewSessionState.from(result.getSessionState());
    boolean valid =
        switch (currentState) {
          case READY -> nextState == InterviewSessionState.PREPARING;
          case PREPARING ->
              nextState == InterviewSessionState.IN_PROGRESS
                  || nextState == InterviewSessionState.PAUSED
                  || nextState == InterviewSessionState.ENDED;
          case IN_PROGRESS ->
              nextState == InterviewSessionState.PAUSED || nextState == InterviewSessionState.ENDED;
          case PAUSED ->
              nextState == InterviewSessionState.IN_PROGRESS
                  || nextState == InterviewSessionState.ENDED;
          case ENDED -> false;
        };

    if (!valid) {
      log.warn(
          "[SessionID: {}] 허용되지 않은 세션 상태 전이: {} -> {}",
          result.getSessionId(),
          currentState,
          nextState);
      throw new InterviewException(ErrorCode.VALIDATION_ERROR);
    }
  }

  private InterviewResult getValidatedSession(Long sessionId, Long userId) {
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_NOT_FOUND));

    if (!result.getUserId().equals(userId)) {
      log.warn("[보안 경고] 타인 세션 접근 시도 - SessionId: {}, UserId: {}", sessionId, userId);
      throw new InterviewException(ErrorCode.AUTH_UNAUTHORIZED);
    }

    return result;
  }

  private void applyStateChange(
      Long sessionId, InterviewResult result, InterviewSessionState nextState) {
    result.setSessionState(nextState.name());

    if (nextState.shouldPersistQa()) {
      persistQaList(sessionId, result);
    }

    runAfterCommit(
        () -> {
          redisSessionService.updateSessionState(sessionId, nextState.name());

          if (nextState.shouldPersistQa()) {
            redisSessionService.clearQaList(sessionId);

            if (nextState == InterviewSessionState.ENDED) {
              rabbitMQProducer.sendReportRequest(sessionId);
            }
          }
        });
  }

  private void persistQaList(Long sessionId, InterviewResult result) {
    List<String> qaJsonList = redisSessionService.getQaList(sessionId);
    if (qaJsonList == null || qaJsonList.isEmpty()) {
      return;
    }

    for (String qaJson : qaJsonList) {
      try {
        Map<String, Object> qaMap = objectMapper.readValue(qaJson, QA_MAP_TYPE);
        detailRepository.save(
            InterviewDetail.builder()
                .interviewResult(result)
                .qId(Long.valueOf(qaMap.get("quizID").toString()))
                .question((String) qaMap.get("question"))
                .answer((String) qaMap.get("answer"))
                .emotionResult(
                    qaMap.get("emotionResult") != null
                        ? String.valueOf(qaMap.get("emotionResult"))
                        : null)
                .responseTimeSeconds(
                    qaMap.get("responseTimeSeconds") != null
                        ? Integer.valueOf(qaMap.get("responseTimeSeconds").toString())
                        : 0)
                .build());
      } catch (Exception e) {
        log.error("[SessionID: {}] Redis 문답 데이터 DB 저장 실패: {}", sessionId, e.getMessage());
      }
    }
  }

  private void runAfterCommit(Runnable runnable) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      runnable.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            runnable.run();
          }
        });
  }
}
