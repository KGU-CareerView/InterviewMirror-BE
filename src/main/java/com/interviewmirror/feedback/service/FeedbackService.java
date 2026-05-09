package com.interviewmirror.domain.feedback.service;

import com.interviewmirror.domain.feedback.dto.FeedbackEndRequest;
import com.interviewmirror.domain.feedback.dto.FeedbackErrorResponse;
import com.interviewmirror.domain.feedback.dto.FeedbackFrameRequest;
import com.interviewmirror.domain.feedback.dto.FeedbackSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackStreamManager feedbackStreamManager;
    private final FeedbackFrameBuffer feedbackFrameBuffer;
    private final FeedbackAggregationService feedbackAggregationService;
    private final SimpMessagingTemplate messagingTemplate;

    public void analyzeFrame(FeedbackFrameRequest request) {
        try {
            feedbackStreamManager.sendFrame(request);
        } catch (RuntimeException e) {
            publishError(request.getSessionId(), "Failed to analyze feedback frame", "FEEDBACK_FRAME_FAILED");
            throw e;
        }
    }

    public FeedbackSummaryResponse completeSession(FeedbackEndRequest request) {
        try {
            String sessionId = request.getSessionId();
            feedbackStreamManager.completeStream(sessionId);
            feedbackFrameBuffer.flushSession(sessionId);

            FeedbackSummaryResponse response = feedbackAggregationService.aggregateAndSave(sessionId);
            messagingTemplate.convertAndSend("/topic/feedback/" + sessionId + "/completed", response);
            return response;
        } catch (RuntimeException e) {
            publishError(request.getSessionId(), "Failed to complete feedback session", "FEEDBACK_COMPLETE_FAILED");
            throw e;
        }
    }

    private void publishError(String sessionId, String message, String errorCode) {
        messagingTemplate.convertAndSend("/topic/feedback/" + sessionId + "/errors", FeedbackErrorResponse.builder()
                .sessionId(sessionId)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
