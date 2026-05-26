package com.interviewmirror.realtime.controller;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.common.dto.MessageResponse;
import com.interviewmirror.realtime.dto.RealtimeAnswerRequest;
import com.interviewmirror.realtime.dto.RealtimeAudioRequest;
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

  @MessageMapping("/realtime.audio")
  public void analyzeAudio(@Valid @Payload RealtimeAudioRequest request) {
    log.info(
        "[WebSocket REQUEST] destination=/app/realtime.audio payload={{sessionId={}, userId={}, timestamp={}, questionIndex={}, windowMs={}, windowCount={}, transcriptLen={}}}",
        request.getSessionId(),
        request.getUserId(),
        request.getTimestamp(),
        request.getQuestionIndex(),
        request.getWindowMs(),
        request.getWindows() == null ? 0 : request.getWindows().size(),
        request.getTranscript() == null ? 0 : request.getTranscript().length());
    realtimeService.analyzeAudio(request);
  }

  @MessageMapping("/realtime.end")
  public ApiResponse<MessageResponse> completeSession(@Valid @Payload RealtimeEndRequest request) {
    log.info(
        "[WebSocket REQUEST] destination=/app/realtime.end payload={{sessionId={}, includesAudio={}}}",
        request.getSessionId(),
        request.getIncludesAudio());
    MessageResponse response = realtimeService.completeSession(request);
    log.info(
        "[WebSocket RESPONSE] destination=/app/realtime.end payload={{sessionId={}, message={}}}",
        request.getSessionId(),
        response.getMessage());
    return ApiResponse.success(response);
  }

  @MessageMapping("/session.answer")
  public void submitAnswer(@Valid @Payload RealtimeAnswerRequest request) {
    String answerPreview = preview(request.getAnswer());
    boolean isPlaceholder =
        request.getAnswer() == null
            || request.getAnswer().isBlank()
            || "사용자가 답변을 완료했습니다.".equalsIgnoreCase(request.getAnswer().trim());
    log.info(
        "[WebSocket REQUEST] destination=/app/session.answer payload={{sessionId={}, answerLength={}, isPlaceholder={}, answerPreview={}, emotionResult={}, responseTimeSeconds={}}}",
        request.getSessionId(),
        request.getAnswer().length(),
        isPlaceholder,
        answerPreview,
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
