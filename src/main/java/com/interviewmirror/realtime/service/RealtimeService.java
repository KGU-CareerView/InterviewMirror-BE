package com.interviewmirror.realtime.service;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.common.dto.MessageResponse;
import com.interviewmirror.exception.ErrorCode;
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
  private final SimpMessagingTemplate messagingTemplate;

  public void analyzeFrame(RealtimeFrameRequest request) {
    try {
      realtimeStreamManager.sendFrame(request);
    } catch (RuntimeException e) {
      publishError(request.getSessionId(), ErrorCode.REALTIME_FRAME_FAILED);
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
