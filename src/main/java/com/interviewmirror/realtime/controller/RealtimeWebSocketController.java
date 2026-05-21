package com.interviewmirror.realtime.controller;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.common.dto.MessageResponse;
import com.interviewmirror.realtime.dto.RealtimeAnswerRequest;
import com.interviewmirror.realtime.dto.RealtimeEndRequest;
import com.interviewmirror.realtime.dto.RealtimeFrameRequest;
import com.interviewmirror.realtime.service.RealtimeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RealtimeWebSocketController {

  private final RealtimeService realtimeService;

  @MessageMapping("/realtime.frames")
  public void analyzeFrame(@Valid @Payload RealtimeFrameRequest request) {
    log.info(
        "[WebSocket REQUEST] destination=/app/realtime.frames payload={{sessionId={}, userId={}, timestamp={}, faceDetected={}, tensorShape={}, featureCount={}, featureSample={}, bbox={}}}",
        request.getSessionId(),
        request.getUserId(),
        request.getTimestamp(),
        request.isFaceDetected(),
        request.getTensorShape(),
        request.getFeatures().size(),
        featureSample(request.getFeatures()),
        request.getBbox());
    realtimeService.analyzeFrame(request);
  }

  @MessageMapping("/realtime.end")
  public ApiResponse<MessageResponse> completeSession(@Valid @Payload RealtimeEndRequest request) {
    log.info(
        "[WebSocket REQUEST] destination=/app/realtime.end payload={{sessionId={}}}",
        request.getSessionId());
    MessageResponse response = realtimeService.completeSession(request);
    log.info(
        "[WebSocket RESPONSE] destination=/app/realtime.end payload={{sessionId={}, message={}}}",
        request.getSessionId(),
        response.getMessage());
    return ApiResponse.success(response);
  }

  @MessageMapping("/session.answer")
  public void submitAnswer(@Valid @Payload RealtimeAnswerRequest request) {
    log.info(
        "[WebSocket REQUEST] destination=/app/session.answer payload={{sessionId={}, answerLength={}, answerPreview={}, emotionResult={}, responseTimeSeconds={}}}",
        request.getSessionId(),
        request.getAnswer().length(),
        preview(request.getAnswer()),
        request.getEmotionResult(),
        request.getResponseTimeSeconds());
    realtimeService.submitAnswer(request);
  }

  private List<Float> featureSample(List<Float> features) {
    return features.stream().limit(8).toList();
  }

  private String preview(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.length() <= 80 ? value : value.substring(0, 80) + "...";
  }
}
