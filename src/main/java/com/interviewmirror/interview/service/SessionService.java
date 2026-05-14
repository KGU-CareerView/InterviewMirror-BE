package com.interviewmirror.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.infrastructure.AiGrpcClient;
import com.interviewmirror.infrastructure.RabbitMQProducer;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
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
  private final SimpMessagingTemplate messagingTemplate;

  private final AiGrpcClient aiGrpcClient;
  private final RabbitMQProducer rabbitMQProducer;
  private final ObjectMapper objectMapper; // JSON 직렬화용

  @Transactional
  public Long createSession(Long userId) {
    InterviewResult savedResult =
        resultRepository.save(
            InterviewResult.builder()
                .userId(userId)
                .sessionState("pause")
                .createTime(LocalDateTime.now())
                .build());

    Long generatedSessionId = savedResult.getSessionId();
    redisSessionService.updateSessionState(generatedSessionId, "pause");
    return generatedSessionId;
  }

  @Transactional
  public void changeState(Long sessionId, Long userId, String state) {

    // [입력값 검증] 유효한 상태값인지 먼저 확인
    Set<String> validStates = Set.of("start", "pause", "resume", "end");
    if (state == null || !validStates.contains(state.toLowerCase())) {
      log.warn("[SessionID: {}] 잘못된 상태 변경 요청 시도 - 입력된 상태값: {}", sessionId, state);
      throw new InterviewException(ErrorCode.VALIDATION_ERROR);
    }

    // ✨ [리팩토링 완료] 공통 검증 로직을 사용하여 코드가 1줄로 단축되었습니다!
    InterviewResult result = getValidatedSession(sessionId, userId);

    result.setSessionState(state);

    // [DB 작업] pause/end 시 문답 리스트 DB 저장
    if ("pause".equalsIgnoreCase(state) || "end".equalsIgnoreCase(state)) {
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
            redisSessionService.updateSessionState(sessionId, state);

            if ("pause".equalsIgnoreCase(state) || "end".equalsIgnoreCase(state)) {
              redisSessionService.clearQaList(sessionId);

              if ("end".equalsIgnoreCase(state)) {
                rabbitMQProducer.sendReportRequest(sessionId);
              }
            }
          }
        });
  }

  @Async("aiTaskExecutor")
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
      messagingTemplate.convertAndSend(
          "/topic/session/" + sessionId + "/error",
          ApiResponse.fail(ErrorCode.SERVER_INTERNAL_ERROR));
      return;
    }

    if (!redisSessionService.lockQuestionGeneration(sessionId)) {
      log.warn("[SessionID: {}] AI 질문 생성 중복 요청 감지. 이미 처리 중입니다.", sessionId);
      messagingTemplate.convertAndSend(
          "/topic/session/" + sessionId + "/error",
          Map.of(
              "type", "PROCESSING_WARNING",
              "message", "현재 AI가 답변을 분석하여 질문을 생성 중입니다. 잠시만 기다려주세요."));
      return;
    }

    try {
      String nextQuestion = aiGrpcClient.generateNextQuestion(sessionId, answer);
      redisSessionService.setLastQuestion(sessionId, nextQuestion);
      messagingTemplate.convertAndSend(
          "/topic/session/" + sessionId + "/question",
          Map.of("type", "NEXT_QUESTION", "question", nextQuestion));
    } catch (Exception e) {
      messagingTemplate.convertAndSend(
          "/topic/session/" + sessionId + "/error", ApiResponse.fail(ErrorCode.AI_RESPONSE_FAILED));
    } finally {
      redisSessionService.unlockQuestionGeneration(sessionId);
    }
  }

  @Transactional
  public void saveVideoUrl(Long sessionId, Long userId, String videoUrl) {
    InterviewResult result = getValidatedSession(sessionId, userId);

    // 검증을 통과했을 때만 URL 저장
    result.setVideoUrl(videoUrl);
  }

  public String processAndBroadcastEmotion(Long sessionId, String facialData) {
    String emotionResult = aiGrpcClient.analyzeEmotion(sessionId, facialData);

    messagingTemplate.convertAndSend(
        "/topic/session/" + sessionId + "/emotion",
        Map.of("type", "EMOTION_UPDATE", "emotion", emotionResult));

    return emotionResult;
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
   * [시스템 전용] 웹소켓 연결 끊김 등 비정상 종료 시 자동으로 세션을 pause 처리합니다. 사용자가 직접 요청하는 것이 아니므로 userId 권한 검증을 생략합니다.
   */
  @Transactional
  public void autoPauseSession(Long sessionId) {
    // 1. 세션 조회 (만약 이미 없거나 끝난 세션이면 무시)
    InterviewResult result = resultRepository.findById(sessionId).orElse(null);
    if (result == null || "end".equalsIgnoreCase(result.getSessionState())) {
      return;
    }

    log.info("[SessionID: {}] 비정상 종료 감지. 시스템이 자동으로 pause 상태로 전환합니다.", sessionId);
    result.setSessionState("pause");

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
            redisSessionService.updateSessionState(sessionId, "pause");
            redisSessionService.clearQaList(sessionId);
          }
        });
  }
}
