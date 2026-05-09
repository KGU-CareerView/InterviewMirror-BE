package com.interviewmirror.domain.feedback.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackFrameRequest {

  @NotBlank(message = "sessionId cannot be blank")
  private String sessionId;

  private String userId;

  @Builder.Default
  @NotEmpty(message = "tensorShape cannot be empty")
  private List<Integer> tensorShape = new ArrayList<>();

  @Builder.Default
  @NotEmpty(message = "features cannot be empty")
  private List<Float> features = new ArrayList<>();

  @NotNull(message = "timestamp cannot be null")
  @PositiveOrZero(message = "timestamp must be zero or positive")
  private Long timestamp;

  private boolean faceDetected;

  @Valid private BoundingBoxDto bbox;
}
