package com.interviewmirror.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.infrastructure.RabbitMQProducer;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import com.interviewmirror.realtime.service.RealtimeMessagePublisher;
import com.interviewmirror.realtime.service.RealtimeQuestionGenerationService;
import java.time.LocalDateTime;
import java.util.HashMap;
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
public class SessionService {
  private final InterviewResultRepository resultRepository;
  private final InterviewDetailRepository detailRepository;
  private final RedisSessionService redisSessionService;

  private final RealtimeQuestionGenerationService questionGenerationService;
  private final RealtimeMessagePublisher realtimeMessagePublisher;
  private final RabbitMQProducer rabbitMQProducer;
  private final ObjectMapper objectMapper; // JSON 직렬화용

  @Transactional
  public Long createSession(Long userId) {
    InterviewResult savedResult =
        resultRepository.save(
            InterviewResult.builder()
                .userId(userId)
                .sessionState(InterviewSessionState.READY.name())
                .createTime(LocalDateTime.now())
                .build());

    Long generatedSessionId = savedResult.getSessionId();
    redisSessionService.updateSessionState(generatedSessionId, InterviewSessionState.READY.name());
    return generatedSessionId;
  }

  @Transactional
  public void changeState(Long sessionId, Long userId, String state) {
    InterviewSessionState nextState = InterviewSessionState.from(state);

    // ✨ [리팩토링 완료] 공통 검증 로직을 사용하여 코드가 1줄로 단축되었습니다!
    InterviewResult result = getValidatedSession(sessionId, userId);
    applyStateChange(sessionId, result, nextState);
  }

  @Transactional
  public void changeStateBySystem(Long sessionId, InterviewSessionState nextState) {
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_NOT_FOUND));
    applyStateChange(sessionId, result, nextState);
  }

  private void applyStateChange(
      Long sessionId, InterviewResult result, InterviewSessionState nextState) {
    result.setSessionState(nextState.name());

    // [DB 작업] PAUSED/ENDED 시 문답 리스트 DB 저장
    if (nextState.shouldPersistQa()) {
      List<String> qaJsonList = redisSessionService.getQaList(sessionId);

      if (qaJsonList != null && !qaJsonList.isEmpty()) {
        for (String qaJson : qaJsonList) {
          try {
            Map<String, Object> qaMap = objectMapper.readValue(qaJson, Map.class);
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
    }

    // [핵심 안전장치] DB 트랜잭션이 성공적으로 '커밋'된 직후에만 실행될 로직 예약
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            redisSessionService.updateSessionState(sessionId, nextState.name());

            if (nextState.shouldPersistQa()) {
              redisSessionService.clearQaList(sessionId);

              if (nextState == InterviewSessionState.ENDED) {
                rabbitMQProducer.sendReportRequest(sessionId);
              }
            }
          }
        });
  }

  public void processAnswerAndGenerateQuestion(
      Long sessionId, String answer, String emotionResult, Integer responseTimeSeconds) {
    String question = redisSessionService.getLastQuestion(sessionId);
    String safeQuestion = (question != null) ? question : "";

    try {
      Map<String, Object> qaData = new HashMap<>();
      qaData.put("quizID", System.currentTimeMillis());
      qaData.put("question", safeQuestion);
      qaData.put("answer", answer);
      qaData.put("emotionResult", emotionResult);
      qaData.put("responseTimeSeconds", responseTimeSeconds);

      String qaJson = objectMapper.writeValueAsString(qaData);
      redisSessionService.addQaToRedis(sessionId, qaJson);
    } catch (Exception e) {
      log.error("Redis 문답 JSON 직렬화 실패: {}", e.getMessage());
      realtimeMessagePublisher.publishSessionError(sessionId, ErrorCode.SERVER_INTERNAL_ERROR);
      return;
    }

    questionGenerationService.generateFollowUpQuestion(sessionId, safeQuestion, answer);
  }

  @Transactional
  public void saveVideoUrl(Long sessionId, Long userId, String videoUrl) {
    InterviewResult result = getValidatedSession(sessionId, userId);

    // 검증을 통과했을 때만 URL 저장
    result.setVideoUrl(videoUrl);
  }

  /**
   * [공통 검증 로직] 세션 ID로 DB에서 세션을 찾고, 요청한 유저의 소유가 맞는지 검증합니다. 검증에 성공하면 InterviewResult 객체를 반환하고, 실패하면
   * 예외를 던집니다.
   */
  @Transactional(readOnly = true)
  public InterviewResult getValidatedSession(Long sessionId, Long userId) {
    // 1. 세션 존재 여부 확인
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_NOT_FOUND));

    // 2. 소유권 검증 (IDOR 방어)
    if (!result.getUserId().equals(userId)) {
      log.warn("[보안 경고] 타인 세션 접근 시도 - SessionId: {}, UserId: {}", sessionId, userId);
      throw new InterviewException(ErrorCode.AUTH_UNAUTHORIZED);
    }

    return result;
  }

  /**
   * [시스템 전용] 웹소켓 연결 끊김 등 비정상 종료 시 자동으로 세션을 PAUSED 처리합니다. 사용자가 직접 요청하는 것이 아니므로 userId 권한 검증을 생략합니다.
   */
  @Transactional
  public void autoPauseSession(Long sessionId) {
    // 1. 세션 조회 (만약 이미 없거나 끝난 세션이면 무시)
    InterviewResult result = resultRepository.findById(sessionId).orElse(null);
    if (result == null || InterviewSessionState.ENDED.name().equals(result.getSessionState())) {
      return;
    }

    log.info("[SessionID: {}] 비정상 종료 감지. 시스템이 자동으로 PAUSED 상태로 전환합니다.", sessionId);
    result.setSessionState(InterviewSessionState.PAUSED.name());

    // 2. Redis에 임시 저장되어 있던 문답 내역을 DB로 안전하게 대피 (기존 로직 재사용)
    List<String> qaJsonList = redisSessionService.getQaList(sessionId);
    if (qaJsonList != null && !qaJsonList.isEmpty()) {
      for (String qaJson : qaJsonList) {
        try {
          Map<String, Object> qaMap = objectMapper.readValue(qaJson, Map.class);
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
          log.error("자동 일시정지 중 DB 저장 실패: {}", e.getMessage());
        }
      }
    }

    // 3. 트랜잭션 성공 시 Redis 상태 업데이트 및 리스트 초기화
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            redisSessionService.updateSessionState(sessionId, InterviewSessionState.PAUSED.name());
            redisSessionService.clearQaList(sessionId);
          }
        });
  }
}
