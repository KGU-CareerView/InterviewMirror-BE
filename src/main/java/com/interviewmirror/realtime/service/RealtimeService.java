package com.interviewmirror.realtime.service;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.common.dto.MessageResponse;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.realtime.dto.RealtimeAnswerRequest;
import com.interviewmirror.realtime.dto.RealtimeEndRequest;
import com.interviewmirror.realtime.dto.RealtimeFrameRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeService {

  private final RealtimeStreamManager realtimeStreamManager;
  private final RealtimeFrameBuffer realtimeFrameBuffer;
  private final SessionService sessionService;
  private final RealtimeQuestionGenerationService questionGenerationService;
  private final RealtimeMessagePublisher realtimeMessagePublisher;
  private final SimpMessagingTemplate messagingTemplate;

  public void analyzeFrame(RealtimeFrameRequest request) {
    try {
      realtimeStreamManager.sendFrame(request);
    } catch (RuntimeException e) {
      publishError(request.getSessionId(), ErrorCode.REALTIME_FRAME_FAILED);
      throw e;
    }
  }

  public void submitAnswer(RealtimeAnswerRequest request) {
    try {
      String previousQuestion =
          sessionService.recordAnswer(
              request.getSessionId(),
              request.getAnswer(),
              request.getEmotionResult(),
              request.getResponseTimeSeconds());

      questionGenerationService.generateFollowUpQuestion(
          request.getSessionId(), previousQuestion, request.getAnswer());
    } catch (RuntimeException e) {
      ErrorCode errorCode =
          e instanceof InterviewException interviewException
              ? interviewException.getErrorCode()
              : ErrorCode.SERVER_INTERNAL_ERROR;
      realtimeMessagePublisher.publishSessionError(request.getSessionId(), errorCode);
      throw e;
    }
  }

  public MessageResponse completeSession(RealtimeEndRequest request) {
    try {
      String sessionId = request.getSessionId();
      realtimeStreamManager.completeStream(sessionId);
      realtimeFrameBuffer.flushSession(sessionId);

      MessageResponse response = new MessageResponse("Realtime session completed.");
      messagingTemplate.convertAndSend(
          "/topic/realtime/" + sessionId + "/completed", ApiResponse.success(response));
      return response;
    } catch (RuntimeException e) {
      publishError(request.getSessionId(), ErrorCode.REALTIME_COMPLETE_FAILED);
      throw e;
    }
  }

  private void publishError(String sessionId, ErrorCode errorCode) {
    messagingTemplate.convertAndSend(
        "/topic/realtime/" + sessionId + "/errors", ApiResponse.fail(errorCode));
  }
}
