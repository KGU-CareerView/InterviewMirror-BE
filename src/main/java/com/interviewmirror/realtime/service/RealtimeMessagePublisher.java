package com.interviewmirror.realtime.service;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeMessagePublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public void publishNextQuestion(Long sessionId, String question) {
    log.info(
        "[WebSocket RESPONSE] destination=/topic/session/{}/question payload={{type=NEXT_QUESTION, questionPreview={}}}",
        sessionId,
        preview(question));
    messagingTemplate.convertAndSend(
        "/topic/session/" + sessionId + "/question",
        Map.of("type", "NEXT_QUESTION", "question", question));
  }

  public void publishInitialQuestionsReady(
      Long sessionId, String firstQuestion, List<Map<String, Object>> questions) {
    log.info(
        "[WebSocket RESPONSE] destination=/topic/session/{}/question payload={{type=INITIAL_QUESTIONS_READY, firstQuestionPreview={}, questionCount={}}}",
        sessionId,
        preview(firstQuestion),
        questions.size());
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
    log.warn(
        "[WebSocket RESPONSE] destination=/topic/session/{}/error payload={{type=PROCESSING_WARNING}}",
        sessionId);
    messagingTemplate.convertAndSend(
        "/topic/session/" + sessionId + "/error",
        Map.of(
            "type", "PROCESSING_WARNING",
            "message", "현재 AI가 답변을 분석하여 질문을 생성 중입니다. 잠시만 기다려주세요."));
  }

  public void publishSessionError(Long sessionId, ErrorCode errorCode) {
    log.warn(
        "[WebSocket RESPONSE] destination=/topic/session/{}/error payload={{errorCode={}}}",
        sessionId,
        errorCode);
    messagingTemplate.convertAndSend(
        "/topic/session/" + sessionId + "/error", ApiResponse.fail(errorCode));
  }

  public void publishAudioFeedback(String sessionId, String type, Map<String, Object> data) {
    log.info(
        "[WebSocket RESPONSE] destination=/topic/realtime/{}/audio payload={{type={}, status={}}}",
        sessionId,
        type,
        data.get("status"));
    messagingTemplate.convertAndSend(
        "/topic/realtime/" + sessionId + "/audio", Map.of("type", type, "data", data));
  }

  private String preview(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.length() <= 80 ? value : value.substring(0, 80) + "...";
  }
}
