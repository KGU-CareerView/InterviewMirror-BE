package com.interviewmirror.realtime.service;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.grpc.proto.AnalysisResponse;
import com.interviewmirror.grpc.proto.FeatureRequest;
import com.interviewmirror.realtime.client.AiGrpcClient;
import com.interviewmirror.realtime.dto.RealtimeFrameRequest;
import com.interviewmirror.realtime.dto.RealtimeResponse;
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
public class RealtimeStreamManager {

  private final AiGrpcClient aiGrpcClient;
  private final RealtimeGrpcMapper realtimeGrpcMapper;
  private final RealtimeFrameBuffer realtimeFrameBuffer;
  private final SimpMessagingTemplate messagingTemplate;
  private final Map<String, StreamObserver<FeatureRequest>> streams = new ConcurrentHashMap<>();

  public void sendFrame(RealtimeFrameRequest request) {
    FeatureRequest featureRequest = realtimeGrpcMapper.toFeatureRequest(request);
    StreamObserver<FeatureRequest> stream =
        streams.computeIfAbsent(request.getSessionId(), this::createStream);

    try {
      log.debug(
          "AI realtime stream request: sessionId={} userId={} timestamp={} faceDetected={} tensorShapeCount={} featureCount={}",
          featureRequest.getSessionId(),
          featureRequest.getUserId(),
          featureRequest.getTimestamp(),
          featureRequest.getFaceDetected(),
          featureRequest.getTensorShapeCount(),
          featureRequest.getFeaturesCount());
      stream.onNext(featureRequest);
    } catch (RuntimeException e) {
      streams.remove(request.getSessionId());
      log.error(
          "AI realtime stream request failed: sessionId={} timestamp={}",
          request.getSessionId(),
          request.getTimestamp(),
          e);
      throw new BusinessException(ErrorCode.REALTIME_STREAM_SEND_FAILED, e);
    }
  }

  public void completeStream(String sessionId) {
    StreamObserver<FeatureRequest> stream = streams.remove(sessionId);
    if (stream != null) {
      log.info("AI realtime stream completion requested: sessionId={}", sessionId);
      stream.onCompleted();
    } else {
      log.debug(
          "AI realtime stream completion skipped, active stream not found: sessionId={}",
          sessionId);
    }
  }

  private StreamObserver<FeatureRequest> createStream(String sessionId) {
    log.info("AI realtime stream created: sessionId={}", sessionId);
    StreamObserver<AnalysisResponse> responseObserver =
        new StreamObserver<>() {
          @Override
          public void onNext(AnalysisResponse analysisResponse) {
            log.info(
                "AI realtime stream response: sessionId={} userId={} label={} confidence={} timestamp={} faceDetected={}",
                analysisResponse.getSessionId(),
                analysisResponse.getUserId(),
                analysisResponse.getLabel(),
                analysisResponse.getConfidence(),
                analysisResponse.getTimestamp(),
                analysisResponse.getFaceDetected());
            RealtimeResponse response = realtimeGrpcMapper.toRealtimeResponse(analysisResponse);
            log.info(
                "[WebSocket RESPONSE] destination=/topic/realtime/{} payload={{label={}, confidence={}}}",
                response.getSessionId(),
                response.getLabel(),
                response.getConfidence());
            messagingTemplate.convertAndSend(
                "/topic/realtime/" + response.getSessionId(), ApiResponse.success(response));
            realtimeFrameBuffer.add(response);
          }

          @Override
          public void onError(Throwable throwable) {
            streams.remove(sessionId);
            log.error("AI realtime stream failed for session {}", sessionId, throwable);
            log.warn(
                "[WebSocket RESPONSE] destination=/topic/realtime/{}/errors payload={{errorCode={}}}",
                sessionId,
                ErrorCode.AI_STREAM_FAILED);
            messagingTemplate.convertAndSend(
                "/topic/realtime/" + sessionId + "/errors",
                ApiResponse.fail(ErrorCode.AI_STREAM_FAILED));
          }

          @Override
          public void onCompleted() {
            streams.remove(sessionId);
            log.debug("AI realtime stream completed for session {}", sessionId);
          }
        };

    return aiGrpcClient.startAnalysisStream(responseObserver);
  }
}
