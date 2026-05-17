package com.interviewmirror.realtime.controller;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.common.dto.MessageResponse;
import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.realtime.dto.RealtimeAnswerRequest;
import com.interviewmirror.realtime.dto.RealtimeEndRequest;
import com.interviewmirror.realtime.dto.RealtimeFrameRequest;
import com.interviewmirror.realtime.service.RealtimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class RealtimeWebSocketController {

  private final RealtimeService realtimeService;
  private final SessionService sessionService;

  @MessageMapping("/realtime.frames")
  public void analyzeFrame(@Valid @Payload RealtimeFrameRequest request) {
    realtimeService.analyzeFrame(request);
  }

  @MessageMapping("/realtime.end")
  public ApiResponse<MessageResponse> completeSession(@Valid @Payload RealtimeEndRequest request) {
    return ApiResponse.success(realtimeService.completeSession(request));
  }

  @MessageMapping("/session.answer")
  public void submitAnswer(@Valid @Payload RealtimeAnswerRequest request) {
    sessionService.processAnswerAndGenerateQuestion(
        request.getSessionId(),
        request.getAnswer(),
        request.getEmotionResult(),
        request.getResponseTimeSeconds());
  }
}
