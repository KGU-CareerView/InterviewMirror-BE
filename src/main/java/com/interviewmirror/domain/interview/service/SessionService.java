package com.interviewmirror.domain.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.RabbitMQProducer;
import com.interviewmirror.domain.interview.entity.InterviewDetail;
import com.interviewmirror.domain.interview.entity.InterviewResult;
import com.interviewmirror.domain.interview.repository.InterviewDetailRepository;
import com.interviewmirror.domain.interview.repository.InterviewResultRepository;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.ErrorResponse;
import com.interviewmirror.exception.InterviewException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  public Long createSession() {
    long userId = 1L;
    // 1. DB에 세션 데이터를 먼저 저장하여 AUTO_INCREMENT로 안전하게 발급된 ID를 얻습니다.
    InterviewResult savedResult =
        resultRepository.save(
            InterviewResult.builder()
                .userId(userId)
                .sessionState("pause")
                .createTime(LocalDateTime.now())
                .build());

    // 2. DB가 자동 생성해준 안전한 세션 ID를 꺼내옵니다.
    Long generatedSessionId = savedResult.getSessionId();

    // 3. 발급받은 ID를 사용해 Redis에 상태를 저장합니다.
    redisSessionService.updateSessionState(generatedSessionId, "pause");

    // 4. 발급된 ID를 컨트롤러로 반환합니다.
    return generatedSessionId;
  }

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
    redisSessionService.updateSessionState(sessionId, state);

    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_EXPIRED));
    result.setSessionState(state);

    if ("pause".equalsIgnoreCase(state) || "end".equalsIgnoreCase(state)) {
      List<String> qaJsonList = redisSessionService.getQaList(sessionId);

      // 저장할 데이터가 있을 때만 DB 저장 로직 수행
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
            log.error("[SessionID: {}] Redis 문답 데이터 파싱 및 DB 저장 실패. 상세 에러: ", sessionId, e);
            continue;
          }
        }

        //  DB에 옮겨 담았으므로 Redis에 쌓인 문답 리스트는 초기화(삭제)
        redisSessionService.clearQaList(sessionId);
      }

      if ("end".equalsIgnoreCase(state)) { // 면접세션 끝나면 결과요청
        rabbitMQProducer.sendReportRequest(sessionId);
      }
    }
  }

  @Async
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
          ErrorResponse.builder()
              .status(500)
              .errorCode("SERVER_INTERNAL_ERROR")
              .message("데이터 저장 중 오류가 발생했습니다.")
              .build());
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
          "/topic/session/" + sessionId + "/error",
          ErrorResponse.builder()
              .status(502)
              .errorCode("AI_RESPONSE_FAILED")
              .message("AI 응답 생성 중 오류가 발생했습니다.")
              .build());
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
