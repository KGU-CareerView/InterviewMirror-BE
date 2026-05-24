package com.interviewmirror.realtime.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RealtimeAudioFeatures {

  private Double rms;
  private Double zeroCrossingRate;
  private Boolean isSpeaking;
  private Long speechDurationMs;
  private Long silenceDurationMs;
  private Double peakAmplitude;
}
