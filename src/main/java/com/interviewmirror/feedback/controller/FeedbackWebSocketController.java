package com.interviewmirror.domain.feedback.controller;

import com.interviewmirror.domain.feedback.dto.FeedbackEndRequest;
import com.interviewmirror.domain.feedback.dto.FeedbackFrameRequest;
import com.interviewmirror.domain.feedback.dto.FeedbackSummaryResponse;
import com.interviewmirror.domain.feedback.service.FeedbackService;
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
  public FeedbackSummaryResponse completeSession(@Valid @Payload FeedbackEndRequest request) {
    return feedbackService.completeSession(request);
  }
}
