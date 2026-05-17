package com.interviewmirror.realtime.service;

import com.interviewmirror.grpc.proto.AnalysisResponse;
import com.interviewmirror.grpc.proto.BoundingBox;
import com.interviewmirror.grpc.proto.FeatureRequest;
import com.interviewmirror.realtime.dto.BoundingBoxDto;
import com.interviewmirror.realtime.dto.RealtimeFrameRequest;
import com.interviewmirror.realtime.dto.RealtimeResponse;
import org.springframework.stereotype.Component;

@Component
public class RealtimeGrpcMapper {

  public FeatureRequest toFeatureRequest(RealtimeFrameRequest request) {
    FeatureRequest.Builder builder =
        FeatureRequest.newBuilder()
            .setSessionId(request.getSessionId())
            .setUserId(valueOrEmpty(request.getUserId()))
            .addAllTensorShape(request.getTensorShape())
            .addAllFeatures(request.getFeatures())
            .setTimestamp(request.getTimestamp())
            .setFaceDetected(request.isFaceDetected())
            .setBbox(toGrpcBoundingBox(request.getBbox()));

    return builder.build();
  }

  public RealtimeResponse toRealtimeResponse(AnalysisResponse response) {
    return RealtimeResponse.builder()
        .sessionId(response.getSessionId())
        .userId(response.getUserId())
        .timestamp(response.getTimestamp())
        .label(response.getLabel())
        .confidence(response.getConfidence())
        .feedback(response.getFeedback())
        .faceDetected(response.getFaceDetected())
        .bbox(toDtoBoundingBox(response.getBbox()))
        .build();
  }

  private BoundingBox toGrpcBoundingBox(BoundingBoxDto bbox) {
    if (bbox == null) {
      return BoundingBox.getDefaultInstance();
    }

    return BoundingBox.newBuilder()
        .setX1(bbox.getX1())
        .setY1(bbox.getY1())
        .setX2(bbox.getX2())
        .setY2(bbox.getY2())
        .build();
  }

  private BoundingBoxDto toDtoBoundingBox(BoundingBox bbox) {
    if (bbox == null) {
      return null;
    }

    return BoundingBoxDto.builder()
        .x1(bbox.getX1())
        .y1(bbox.getY1())
        .x2(bbox.getX2())
        .y2(bbox.getY2())
        .build();
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
