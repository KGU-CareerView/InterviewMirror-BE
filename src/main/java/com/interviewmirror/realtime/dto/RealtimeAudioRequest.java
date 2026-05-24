package com.interviewmirror.realtime.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RealtimeAudioRequest {

  @NotBlank private String sessionId;

  private String userId;

  @NotNull private Long timestamp;

  @NotNull private Integer questionIndex;

  @NotNull private Integer windowMs;

  @Valid @NotNull private RealtimeAudioFeatures features;
}
