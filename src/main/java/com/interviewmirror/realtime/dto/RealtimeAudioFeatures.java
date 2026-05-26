package com.interviewmirror.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RealtimeAudioFeatures {

  private Double rms;
  private Double zeroCrossingRate;
  private Boolean isSpeaking;
  private Long speechDurationMs;
  private Long silenceDurationMs;
  private Double peakAmplitude;
}
