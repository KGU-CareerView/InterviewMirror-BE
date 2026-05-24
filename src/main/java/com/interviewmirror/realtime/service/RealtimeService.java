package com.interviewmirror.realtime.service;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.common.dto.MessageResponse;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.realtime.dto.RealtimeAnswerRequest;
import com.interviewmirror.realtime.dto.RealtimeAudioFeatures;
import com.interviewmirror.realtime.dto.RealtimeAudioRequest;
import com.interviewmirror.realtime.dto.RealtimeEndRequest;
import com.interviewmirror.realtime.dto.RealtimeFrameRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeService {

  private final RealtimeStreamManager realtimeStreamManager;
  private final RealtimeFrameBuffer realtimeFrameBuffer;
  private final SessionService sessionService;
  private final RealtimeQuestionGenerationService questionGenerationService;
  private final RealtimeMessagePublisher realtimeMessagePublisher;
  private final RedisSessionService redisSessionService;
  private final SimpMessagingTemplate messagingTemplate;

  private static final int LONG_PAUSE_WINDOW_THRESHOLD = 5;
  private static final double TOO_QUIET_RMS_THRESHOLD = 0.015;

  public void analyzeAudio(RealtimeAudioRequest request) {
    RealtimeAudioFeatures features = request.getFeatures();
    Long sessionIdLong = Long.parseLong(request.getSessionId());

    if (Boolean.TRUE.equals(features.getIsSpeaking())) {
      redisSessionService.resetSilenceWindows(sessionIdLong);

      if (features.getRms() != null && features.getRms() < TOO_QUIET_RMS_THRESHOLD) {
        realtimeMessagePublisher.publishAudioFeedback(
            request.getSessionId(),
            "VOLUME_FEEDBACK",
            Map.of(
                "status", "TOO_QUIET",
                "avgRms", features.getRms(),
                "message", "목소리가 작습니다. 조금 더 또렷하게 말씀해보세요."));
      }
    } else {
      long silenceWindows = redisSessionService.incrementSilenceWindows(sessionIdLong);
      if (silenceWindows == LONG_PAUSE_WINDOW_THRESHOLD) {
        realtimeMessagePublisher.publishAudioFeedback(
            request.getSessionId(),
            "PAUSE_FEEDBACK",
            Map.of(
                "status", "LONG_PAUSE",
                "silenceSeconds", silenceWindows,
                "message", "침묵이 길어지고 있습니다. 핵심부터 이어서 답변해보세요."));
      }
    }

    if (features.getZeroCrossingRate() != null) {
      redisSessionService.appendZcrSample(
          sessionIdLong, request.getQuestionIndex(), features.getZeroCrossingRate());
    }
  }

  public void analyzeFrame(RealtimeFrameRequest request) {
    try {
      log.debug(
          "Processing realtime frame: sessionId={} userId={} timestamp={} featureCount={}",
          request.getSessionId(),
          request.getUserId(),
          request.getTimestamp(),
          request.getFeatures().size());
      realtimeStreamManager.sendFrame(request);
      log.debug(
          "Realtime frame forwarded to AI stream: sessionId={} timestamp={}",
          request.getSessionId(),
          request.getTimestamp());
    } catch (RuntimeException e) {
      log.error(
          "Realtime frame processing failed: sessionId={} timestamp={}",
          request.getSessionId(),
          request.getTimestamp(),
          e);
      publishError(request.getSessionId(), ErrorCode.REALTIME_FRAME_FAILED);
      throw e;
    }
  }

  public void submitAnswer(RealtimeAnswerRequest request) {
    try {
      log.info(
          "Processing submitted answer: sessionId={} answerLength={} emotionResult={} responseTimeSeconds={}",
          request.getSessionId(),
          request.getAnswer().length(),
          request.getEmotionResult(),
          request.getResponseTimeSeconds());
      String previousQuestion =
          sessionService.recordAnswer(
              request.getSessionId(),
              request.getAnswer(),
              request.getEmotionResult(),
              request.getResponseTimeSeconds(),
              request.getAudioSummary());

      log.info(
          "Answer recorded, requesting follow-up question: sessionId={} previousQuestionPreview={}",
          request.getSessionId(),
          preview(previousQuestion));
      questionGenerationService.generateFollowUpQuestion(
          request.getSessionId(), previousQuestion, request.getAnswer());
    } catch (RuntimeException e) {
      ErrorCode errorCode =
          e instanceof InterviewException interviewException
              ? interviewException.getErrorCode()
              : ErrorCode.SERVER_INTERNAL_ERROR;
      log.error(
          "Submitted answer processing failed: sessionId={} errorCode={}",
          request.getSessionId(),
          errorCode,
          e);
      realtimeMessagePublisher.publishSessionError(request.getSessionId(), errorCode);
      throw e;
    }
  }

  public MessageResponse completeSession(RealtimeEndRequest request) {
    try {
      String sessionId = request.getSessionId();
      log.info("Completing realtime session: sessionId={}", sessionId);
      realtimeStreamManager.completeStream(sessionId);
      realtimeFrameBuffer.flushSession(sessionId);

      MessageResponse response = new MessageResponse("Realtime session completed.");
      log.info(
          "[WebSocket RESPONSE] destination=/topic/realtime/{}/completed payload={{message={}}}",
          sessionId,
          response.getMessage());
      messagingTemplate.convertAndSend(
          "/topic/realtime/" + sessionId + "/completed", ApiResponse.success(response));
      return response;
    } catch (RuntimeException e) {
      log.error("Realtime session completion failed: sessionId={}", request.getSessionId(), e);
      publishError(request.getSessionId(), ErrorCode.REALTIME_COMPLETE_FAILED);
      throw e;
    }
  }

  private void publishError(String sessionId, ErrorCode errorCode) {
    log.warn(
        "[WebSocket RESPONSE] destination=/topic/realtime/{}/errors payload={{errorCode={}}}",
        sessionId,
        errorCode);
    messagingTemplate.convertAndSend(
        "/topic/realtime/" + sessionId + "/errors", ApiResponse.fail(errorCode));
  }

  private String preview(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.length() <= 80 ? value : value.substring(0, 80) + "...";
  }
}
