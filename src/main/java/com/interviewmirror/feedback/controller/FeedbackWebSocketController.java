package com.interviewmirror.feedback.controller;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.common.dto.MessageResponse;
import com.interviewmirror.feedback.dto.FeedbackEndRequest;
import com.interviewmirror.feedback.dto.FeedbackFrameRequest;
import com.interviewmirror.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class FeedbackWebSocketController {

  private final FeedbackService feedbackService;

  @MessageMapping("/feedback.frames")
  public void analyzeFrame(@Valid @Payload FeedbackFrameRequest request) {
    feedbackService.analyzeFrame(request);
  }

  @MessageMapping("/feedback.end")
  public ApiResponse<MessageResponse> completeSession(@Valid @Payload FeedbackEndRequest request) {
    return ApiResponse.success(feedbackService.completeSession(request));
  }
}
