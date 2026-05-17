package com.interviewmirror.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.grpc.proto.AnalysisResponse;
import com.interviewmirror.grpc.proto.FeatureRequest;
import com.interviewmirror.realtime.client.AiGrpcClient;
import com.interviewmirror.realtime.dto.RealtimeFrameRequest;
import com.interviewmirror.realtime.dto.RealtimeResponse;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@DisplayName("RealtimeStreamManager Tests")
class RealtimeStreamManagerTest {

  private final RealtimeGrpcMapper mapper = new RealtimeGrpcMapper();
  private final AiGrpcClient client = mock(AiGrpcClient.class);
  private final RealtimeFrameBuffer frameBuffer = mock(RealtimeFrameBuffer.class);
  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final AtomicReference<StreamObserver<AnalysisResponse>> responseObserver =
      new AtomicReference<>();
  private final AtomicReference<FeatureRequest> requestSent = new AtomicReference<>();

  private final RealtimeStreamManager streamManager =
      new RealtimeStreamManager(client, mapper, frameBuffer, messagingTemplate);

  @BeforeEach
  void setUp() {
    when(client.startAnalysisStream(any()))
        .thenAnswer(
            invocation -> {
              responseObserver.set(invocation.getArgument(0));
              return new StreamObserver<FeatureRequest>() {
                @Override
                public void onNext(FeatureRequest value) {
                  requestSent.set(value);
                }

                @Override
                public void onError(Throwable throwable) {}

                @Override
                public void onCompleted() {}
              };
            });
  }

  @Test
  @DisplayName("should send frame to grpc stream")
  void sendFrame() {
    RealtimeFrameRequest request =
        RealtimeFrameRequest.builder()
            .sessionId("session-1")
            .tensorShape(List.of(1, 3, 2, 2))
            .features(List.of(0.0f, 0.0f, 0.0f, 0.0f))
            .timestamp(1000L)
            .faceDetected(false)
            .build();

    streamManager.sendFrame(request);

    assertThat(requestSent.get()).isNotNull();
    assertThat(requestSent.get().getSessionId()).isEqualTo("session-1");
    assertThat(requestSent.get().getTimestamp()).isEqualTo(1000L);
  }

  @Test
  @DisplayName("should publish and buffer grpc response")
  void publishAndBufferResponse() {
    streamManager.sendFrame(
        RealtimeFrameRequest.builder()
            .sessionId("session-1")
            .tensorShape(List.of(1, 3, 2, 2))
            .features(List.of(0.0f, 0.0f, 0.0f, 0.0f))
            .timestamp(1000L)
            .faceDetected(false)
            .build());

    responseObserver
        .get()
        .onNext(
            AnalysisResponse.newBuilder()
                .setSessionId("session-1")
                .setTimestamp(1000L)
                .setLabel("neutral")
                .setConfidence(0.7f)
                .build());

    verify(messagingTemplate)
        .convertAndSend(eq("/topic/realtime/session-1"), any(ApiResponse.class));
    verify(frameBuffer).add(any(RealtimeResponse.class));
  }
}
