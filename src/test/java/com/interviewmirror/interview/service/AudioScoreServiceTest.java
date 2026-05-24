package com.interviewmirror.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.realtime.dto.AudioSummaryDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AudioScoreServiceTest {

  private final AudioScoreService audioScoreService = new AudioScoreService(new ObjectMapper());

  @Test
  @DisplayName("audioSummary 기반 질문별 음성 점수를 산출한다.")
  void calculateQuestionScore() {
    AudioSummaryDto summary = new AudioSummaryDto();
    ReflectionTestUtils.setField(summary, "speechRatio", 0.7);
    ReflectionTestUtils.setField(summary, "avgRms", 0.04);
    ReflectionTestUtils.setField(summary, "rmsCoV", 0.1);
    ReflectionTestUtils.setField(summary, "wpm", 155);
    ReflectionTestUtils.setField(summary, "pauseCount", 2);
    ReflectionTestUtils.setField(summary, "avgPauseDurationMs", 1200);
    ReflectionTestUtils.setField(summary, "responseLatencyMs", 1500);
    ReflectionTestUtils.setField(summary, "endFadeOut", false);
    ReflectionTestUtils.setField(summary, "fillerWordCount", 0);
    ReflectionTestUtils.setField(summary, "wordCount", 50);
    ReflectionTestUtils.setField(summary, "ttr", 0.7);

    Integer score = audioScoreService.calculateQuestionScore(summary);

    assertThat(score).isEqualTo(100);
  }

  @Test
  @DisplayName("질문별 음성 점수 평균을 세션 음성 종합 점수로 산출한다.")
  void calculateSessionScore() {
    InterviewDetail first = InterviewDetail.builder().audioScore(80).build();
    InterviewDetail second = InterviewDetail.builder().audioScore(100).build();
    InterviewDetail skipped = InterviewDetail.builder().audioScore(null).build();

    Integer score = audioScoreService.calculateSessionScore(List.of(first, second, skipped));

    assertThat(score).isEqualTo(90);
  }

  @Test
  @DisplayName("AI 분석 JSON에 음성 종합 점수를 병합한다.")
  void mergeAudioAnalysis() {
    String merged = audioScoreService.mergeAudioAnalysis("{\"contentScore\":88}", 90, 3);

    assertThat(merged)
        .contains("\"contentScore\":88")
        .contains("\"audio\":{\"overallScore\":90,\"scoredQuestionCount\":3}");
  }
}
