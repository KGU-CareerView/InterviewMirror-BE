package com.interviewmirror.feedback.service;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.common.dto.MessageResponse;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.feedback.dto.FeedbackEndRequest;
import com.interviewmirror.feedback.dto.FeedbackFrameRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackService {

  private final FeedbackStreamManager feedbackStreamManager;
  private final FeedbackFrameBuffer feedbackFrameBuffer;
  private final SimpMessagingTemplate messagingTemplate;

  public void analyzeFrame(FeedbackFrameRequest request) {
    try {
      feedbackStreamManager.sendFrame(request);
    } catch (RuntimeException e) {
      publishError(request.getSessionId(), ErrorCode.FEEDBACK_FRAME_FAILED);
      throw e;
    }
  }

  public MessageResponse completeSession(FeedbackEndRequest request) {
    try {
      String sessionId = request.getSessionId();
      feedbackStreamManager.completeStream(sessionId);
      feedbackFrameBuffer.flushSession(sessionId);

      MessageResponse response = new MessageResponse("Feedback session completed.");
      messagingTemplate.convertAndSend(
          "/topic/feedback/" + sessionId + "/completed", ApiResponse.success(response));
      return response;
    } catch (RuntimeException e) {
      publishError(request.getSessionId(), ErrorCode.FEEDBACK_COMPLETE_FAILED);
      throw e;
    }
  }

  private void publishError(String sessionId, ErrorCode errorCode) {
    messagingTemplate.convertAndSend(
        "/topic/feedback/" + sessionId + "/errors", ApiResponse.fail(errorCode));
  }
}
