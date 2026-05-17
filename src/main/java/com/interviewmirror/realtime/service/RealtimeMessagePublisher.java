package com.interviewmirror.realtime.service;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeMessagePublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public void publishNextQuestion(Long sessionId, String question) {
    messagingTemplate.convertAndSend(
        "/topic/session/" + sessionId + "/question",
        Map.of("type", "NEXT_QUESTION", "question", question));
  }

  public void publishInitialQuestionsReady(
      Long sessionId, String firstQuestion, List<Map<String, Object>> questions) {
    messagingTemplate.convertAndSend(
        "/topic/session/" + sessionId + "/question",
        Map.of(
            "type",
            "INITIAL_QUESTIONS_READY",
            "firstQuestion",
            firstQuestion,
            "questions",
            questions));
  }

  public void publishQuestionProcessingWarning(Long sessionId) {
    messagingTemplate.convertAndSend(
        "/topic/session/" + sessionId + "/error",
        Map.of(
            "type", "PROCESSING_WARNING",
            "message", "현재 AI가 답변을 분석하여 질문을 생성 중입니다. 잠시만 기다려주세요."));
  }

  public void publishSessionError(Long sessionId, ErrorCode errorCode) {
    messagingTemplate.convertAndSend(
        "/topic/session/" + sessionId + "/error", ApiResponse.fail(errorCode));
  }
}
