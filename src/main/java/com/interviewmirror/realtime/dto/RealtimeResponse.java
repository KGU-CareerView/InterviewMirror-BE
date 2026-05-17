package com.interviewmirror.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RealtimeResponse {

  private String sessionId;
  private String userId;
  private long timestamp;
  private String label;
  private float confidence;
  private String feedback;
  private boolean faceDetected;
  private BoundingBoxDto bbox;
}
