package com.interviewmirror.realtime.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AudioSummaryDto {

  private Double speechRatio;
  private Double avgRms;
  private Double rmsCoV;
  private Integer wpm;
  private Integer pauseCount;
  private Integer avgPauseDurationMs;
  private Integer maxPauseDurationMs;
  private Integer responseLatencyMs;
  private Boolean endFadeOut;
  private Integer estimatedFillerCount;
  private Integer fillerWordCount;
  private Integer wordCount;
  private Double ttr;
}
