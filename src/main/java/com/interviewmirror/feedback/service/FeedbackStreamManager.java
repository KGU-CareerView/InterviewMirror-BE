package com.interviewmirror.feedback.service;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.feedback.client.EmotionAnalysisClient;
import com.interviewmirror.feedback.dto.FeedbackFrameRequest;
import com.interviewmirror.feedback.dto.FeedbackResponse;
import com.interviewmirror.grpc.proto.AnalysisResponse;
import com.interviewmirror.grpc.proto.FeatureRequest;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackStreamManager {

  private final EmotionAnalysisClient emotionAnalysisClient;
  private final FeedbackGrpcMapper feedbackGrpcMapper;
  private final FeedbackFrameBuffer feedbackFrameBuffer;
  private final SimpMessagingTemplate messagingTemplate;
  private final Map<String, StreamObserver<FeatureRequest>> streams = new ConcurrentHashMap<>();

  public void sendFrame(FeedbackFrameRequest request) {
    FeatureRequest featureRequest = feedbackGrpcMapper.toFeatureRequest(request);
    StreamObserver<FeatureRequest> stream =
        streams.computeIfAbsent(request.getSessionId(), this::createStream);

    try {
      stream.onNext(featureRequest);
    } catch (RuntimeException e) {
      streams.remove(request.getSessionId());
      throw new BusinessException(ErrorCode.FEEDBACK_STREAM_SEND_FAILED, e);
    }
  }

  public void completeStream(String sessionId) {
    StreamObserver<FeatureRequest> stream = streams.remove(sessionId);
    if (stream != null) {
      stream.onCompleted();
    }
  }

  private StreamObserver<FeatureRequest> createStream(String sessionId) {
    StreamObserver<AnalysisResponse> responseObserver =
        new StreamObserver<>() {
          @Override
          public void onNext(AnalysisResponse analysisResponse) {
            FeedbackResponse response = feedbackGrpcMapper.toFeedbackResponse(analysisResponse);
            messagingTemplate.convertAndSend(
                "/topic/feedback/" + response.getSessionId(), ApiResponse.success(response));
            feedbackFrameBuffer.add(response);
          }

          @Override
          public void onError(Throwable throwable) {
            streams.remove(sessionId);
            log.error("AI feedback stream failed for session {}", sessionId, throwable);
            messagingTemplate.convertAndSend(
                "/topic/feedback/" + sessionId + "/errors",
                ApiResponse.fail(ErrorCode.AI_STREAM_FAILED));
          }

          @Override
          public void onCompleted() {
            streams.remove(sessionId);
            log.debug("AI feedback stream completed for session {}", sessionId);
          }
        };

    return emotionAnalysisClient.startAnalysisStream(responseObserver);
  }
}
