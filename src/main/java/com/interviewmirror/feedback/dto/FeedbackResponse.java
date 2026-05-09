package com.interviewmirror.domain.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResponse {

  private String sessionId;
  private String userId;
  private long timestamp;
  private String label;
  private float confidence;
  private String feedback;
  private boolean faceDetected;
  private BoundingBoxDto bbox;
}
