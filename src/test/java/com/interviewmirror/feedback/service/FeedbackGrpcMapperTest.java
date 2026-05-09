package com.interviewmirror.domain.feedback.service;

import com.interviewmirror.domain.feedback.dto.BoundingBoxDto;
import com.interviewmirror.domain.feedback.dto.FeedbackFrameRequest;
import com.interviewmirror.domain.feedback.dto.FeedbackResponse;
import com.interviewmirror.grpc.proto.AnalysisResponse;
import com.interviewmirror.grpc.proto.BoundingBox;
import com.interviewmirror.grpc.proto.FeatureRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeedbackGrpcMapper Tests")
class FeedbackGrpcMapperTest {

    private final FeedbackGrpcMapper mapper = new FeedbackGrpcMapper();

    @Test
    @DisplayName("should map websocket request to grpc FeatureRequest")
    void toFeatureRequest() {
        FeedbackFrameRequest request = FeedbackFrameRequest.builder()
                .sessionId("session-1")
                .userId("user-1")
                .tensorShape(List.of(1, 3, 2, 2))
                .features(List.of(0.1f, 0.2f, 0.3f, 0.4f))
                .timestamp(1000L)
                .faceDetected(true)
                .bbox(BoundingBoxDto.builder()
                        .x1(1)
                        .y1(2)
                        .x2(3)
                        .y2(4)
                        .build())
                .build();

        FeatureRequest result = mapper.toFeatureRequest(request);

        assertThat(result.getSessionId()).isEqualTo("session-1");
        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getTensorShapeList()).containsExactly(1, 3, 2, 2);
        assertThat(result.getFeaturesList()).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        assertThat(result.getTimestamp()).isEqualTo(1000L);
        assertThat(result.getFaceDetected()).isTrue();
        assertThat(result.getBbox().getX1()).isEqualTo(1);
        assertThat(result.getBbox().getY1()).isEqualTo(2);
        assertThat(result.getBbox().getX2()).isEqualTo(3);
        assertThat(result.getBbox().getY2()).isEqualTo(4);
    }

    @Test
    @DisplayName("should map grpc AnalysisResponse to websocket response")
    void toFeedbackResponse() {
        AnalysisResponse response = AnalysisResponse.newBuilder()
                .setSessionId("session-1")
                .setUserId("user-1")
                .setTimestamp(1000L)
                .setLabel("focused")
                .setConfidence(0.9f)
                .setFeedback("Keep steady eye contact.")
                .setFaceDetected(true)
                .setBbox(BoundingBox.newBuilder()
                        .setX1(10)
                        .setY1(20)
                        .setX2(30)
                        .setY2(40)
                        .build())
                .build();

        FeedbackResponse result = mapper.toFeedbackResponse(response);

        assertThat(result.getSessionId()).isEqualTo("session-1");
        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getTimestamp()).isEqualTo(1000L);
        assertThat(result.getLabel()).isEqualTo("focused");
        assertThat(result.getConfidence()).isEqualTo(0.9f);
        assertThat(result.getFeedback()).isEqualTo("Keep steady eye contact.");
        assertThat(result.isFaceDetected()).isTrue();
        assertThat(result.getBbox().getX1()).isEqualTo(10);
        assertThat(result.getBbox().getY1()).isEqualTo(20);
        assertThat(result.getBbox().getX2()).isEqualTo(30);
        assertThat(result.getBbox().getY2()).isEqualTo(40);
    }
}
