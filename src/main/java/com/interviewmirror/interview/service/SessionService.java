package com.interviewmirror.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.RabbitMQProducer;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    // DB에 세션 데이터를 먼저 저장하여 AUTO_INCREMENT로 안전하게 발급된 ID를 얻습니다.
    InterviewResult savedResult =
        resultRepository.save(
            InterviewResult.builder()
                .userId(userId)
                .sessionState("pause")
                .createTime(LocalDateTime.now())
                .build());

    // DB가 자동 생성해준 안전한 세션 ID를 꺼내옵니다.
    Long generatedSessionId = savedResult.getSessionId();

    // 발급받은 ID를 사용해 Redis에 상태를 저장합니다.
    redisSessionService.updateSessionState(generatedSessionId, "pause");

    // 발급된 ID를 컨트롤러로 반환합니다.
    return generatedSessionId;
  }

  @Transactional
  public void changeState(Long sessionId, String state) {
    // [DB 작업] 먼저 수행: 세션 결과(Result) 조회 및 상태 변경
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_EXPIRED));
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
            // DB 저장이 성공했으므로 이제 안전하게 Redis를 업데이트합니다.
            redisSessionService.updateSessionState(sessionId, state);

            if ("pause".equalsIgnoreCase(state) || "end".equalsIgnoreCase(state)) {
              // DB에 옮겨 담았으므로 Redis 리스트 초기화
              redisSessionService.clearQaList(sessionId);

              // 면접이 완전히 끝난 경우에만 AI 서버에 결과 리포트 요청
              if ("end".equalsIgnoreCase(state)) {
                rabbitMQProducer.sendReportRequest(sessionId);
              }
            }
          }
        });
  }

  @Async("aiTaskExecutor")
  public void processAnswerAndGenerateQuestion(Long sessionId, String answer) {

    String question = redisSessionService.getLastQuestion(sessionId);
    String safeQuestion = (question != null) ? question : "";

    // Redis에 문답 저장 (JSON 직렬화)
    try {
      String qaJson =
          objectMapper.writeValueAsString(
              Map.of(
                  "quizID", System.currentTimeMillis(),
                  "question", safeQuestion,
                  "answer", answer));
      redisSessionService.addQaToRedis(sessionId, qaJson);
    } catch (Exception e) {
      log.error("Redis 문답 JSON 직렬화 실패: {}", e.getMessage());

      messagingTemplate.convertAndSend(
          "/topic/session/" + sessionId + "/error",
          ApiResponse.fail(ErrorCode.SERVER_INTERNAL_ERROR));
      return; // 이후 로직(gRPC 호출 등)이 실행되지 않도록 여기서 메서드를 종료합니다.
    }

    // 중복 생성 방지 Lock
    if (!redisSessionService.lockQuestionGeneration(sessionId)) return;

    try {
      // gRPC를 통한 AI 질문 생성 요청
      String nextQuestion = aiGrpcClient.generateNextQuestion(sessionId, answer);

      // 잊지 말고 Redis에 방금 만든 이 질문을 저장해 둡니다 (다음 사이클을 위해)
      redisSessionService.setLastQuestion(sessionId, nextQuestion);

      // WebSocket으로 프론트엔드에 새 질문 전송
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
  public void saveVideoUrl(Long sessionId, String videoUrl) {
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_EXPIRED));
    result.setVideoUrl(videoUrl);
  }
}
