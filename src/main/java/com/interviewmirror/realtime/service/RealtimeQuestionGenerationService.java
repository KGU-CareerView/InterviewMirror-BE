package com.interviewmirror.realtime.service;

import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.grpc.proto.InitialQuestionGenerateResponse;
import com.interviewmirror.grpc.proto.QuestionItem;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.realtime.client.AiGrpcClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeQuestionGenerationService {

  private final AiGrpcClient aiGrpcClient;
  private final InterviewResultRepository resultRepository;
  private final RedisSessionService redisSessionService;
  private final RealtimeMessagePublisher realtimeMessagePublisher;

  @Async("aiTaskExecutor")
  public void generateInitialQuestions(
      Long sessionId, Long userId, InterviewSettingRequest request) {
    if (!redisSessionService.lockQuestionGeneration(sessionId)) {
      log.warn("[SessionID: {}] 초기 질문 생성 중복 요청 감지. 이미 처리 중입니다.", sessionId);
      realtimeMessagePublisher.publishQuestionProcessingWarning(sessionId);
      return;
    }

    try {
      InitialQuestionGenerateResponse response =
          aiGrpcClient.requestInitialQuestions(
              sessionId,
              userId,
              request.getCategory(),
              request.getInterviewType(),
              request.getDifficulty(),
              request.getQuestionCount(),
              request.getTimePerQuestion(),
              request.getResumeContent());

      List<QuestionItem> questions = response.getQuestionsList();
      if (questions.isEmpty()) {
        log.warn("[SessionID: {}] AI 서버가 빈 초기 질문 목록을 응답했습니다.", sessionId);
        realtimeMessagePublisher.publishSessionError(sessionId, ErrorCode.AI_RESPONSE_FAILED);
        return;
      }

      String firstQuestion = questions.get(0).getQuestion();
      redisSessionService.setLastQuestion(sessionId, firstQuestion);
      markSessionInProgress(sessionId);
      realtimeMessagePublisher.publishInitialQuestionsReady(
          sessionId, firstQuestion, questions.stream().map(this::toPayload).toList());
    } catch (Exception e) {
      log.error("[SessionID: {}] 초기 질문 생성 실패", sessionId, e);
      realtimeMessagePublisher.publishSessionError(sessionId, ErrorCode.AI_RESPONSE_FAILED);
    } finally {
      redisSessionService.unlockQuestionGeneration(sessionId);
    }
  }

  @Async("aiTaskExecutor")
  public void generateFollowUpQuestion(Long sessionId, String previousQuestion, String answer) {
    if (!redisSessionService.lockQuestionGeneration(sessionId)) {
      log.warn("[SessionID: {}] AI 질문 생성 중복 요청 감지. 이미 처리 중입니다.", sessionId);
      realtimeMessagePublisher.publishQuestionProcessingWarning(sessionId);
      return;
    }

    try {
      String nextQuestion =
          aiGrpcClient.generateFollowUpQuestion(sessionId, previousQuestion, answer);
      redisSessionService.setLastQuestion(sessionId, nextQuestion);
      realtimeMessagePublisher.publishNextQuestion(sessionId, nextQuestion);
    } catch (Exception e) {
      log.error("[SessionID: {}] 꼬리 질문 생성 실패", sessionId, e);
      realtimeMessagePublisher.publishSessionError(sessionId, ErrorCode.AI_RESPONSE_FAILED);
    } finally {
      redisSessionService.unlockQuestionGeneration(sessionId);
    }
  }

  private void markSessionInProgress(Long sessionId) {
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
    result.setSessionState(InterviewSessionState.IN_PROGRESS.name());
    resultRepository.save(result);
    redisSessionService.updateSessionState(sessionId, InterviewSessionState.IN_PROGRESS.name());
  }

  private Map<String, Object> toPayload(QuestionItem question) {
    return Map.of(
        "index", question.getIndex(),
        "question", question.getQuestion(),
        "tooltip", question.getTooltip(),
        "category", question.getCategory(),
        "intent", question.getIntent(),
        "answerKeywords", question.getAnswerKeywordsList());
  }
}
