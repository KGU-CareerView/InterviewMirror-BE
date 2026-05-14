package com.interviewmirror.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewmirror.feedback.dto.FeedbackResponse;
import com.interviewmirror.feedback.dto.FeedbackSummaryResponse;
import com.interviewmirror.feedback.entity.FeedbackResult;
import com.interviewmirror.feedback.repository.FeedbackBufferRepository;
import com.interviewmirror.feedback.repository.FeedbackRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("FeedbackAggregationService Tests")
@ExtendWith(MockitoExtension.class)
class FeedbackAggregationServiceTest {

  @Mock private FeedbackBufferRepository feedbackBufferRepository;

  @Mock private FeedbackRepository feedbackRepository;

  @InjectMocks private FeedbackAggregationService feedbackAggregationService;

  @Test
  @DisplayName("should aggregate feedback frames and save summary")
  void aggregateAndSave() {
    List<FeedbackResponse> responses =
        List.of(
            response("session-1", 1000L, "stable_confident", 0.8f, true, "Good posture."),
            response("session-1", 2000L, "nervous_anxious", 0.7f, false, "Reduce movement."),
            response(
                "session-1", 3000L, "stable_confident", 0.9f, true, "Keep steady eye contact."));

    when(feedbackRepository.findBySessionId("session-1")).thenReturn(Optional.empty());
    when(feedbackBufferRepository.findAll("session-1")).thenReturn(responses);
    when(feedbackRepository.save(any(FeedbackResult.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FeedbackSummaryResponse result = feedbackAggregationService.aggregateAndSave("session-1");

    assertThat(result.getSessionId()).isEqualTo("session-1");
    assertThat(result.getTotalFrames()).isEqualTo(3);
    assertThat(result.getDominantLabel()).isEqualTo("stable_confident");
    assertThat(result.getAverageConfidence()).isCloseTo(0.8, withinOffset());
    assertThat(result.getStableCount()).isEqualTo(2);
    assertThat(result.getNervousCount()).isEqualTo(1);
    assertThat(result.getNeutralCount()).isZero();
    assertThat(result.getFaceDetectedCount()).isEqualTo(2);
    assertThat(result.getLatestFeedback()).isEqualTo("Keep steady eye contact.");

    verify(feedbackBufferRepository).delete("session-1");
  }

  @Test
  @DisplayName("should return existing result for duplicate completion")
  void aggregateAndSaveIdempotently() {
    FeedbackResult existing =
        FeedbackResult.builder()
            .id(1L)
            .sessionId("session-1")
            .totalFrames(1)
            .dominantLabel("neutral")
            .averageConfidence(0.6)
            .stableCount(0)
            .nervousCount(0)
            .neutralCount(1)
            .faceDetectedCount(1)
            .latestFeedback("Stay centered.")
            .build();

    when(feedbackRepository.findBySessionId("session-1")).thenReturn(Optional.of(existing));

    FeedbackSummaryResponse result = feedbackAggregationService.aggregateAndSave("session-1");

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getDominantLabel()).isEqualTo("neutral");
    assertThat(result.getTotalFrames()).isEqualTo(1);
    assertThat(result.getLatestFeedback()).isEqualTo("Stay centered.");
  }

  private FeedbackResponse response(
      String sessionId,
      long timestamp,
      String label,
      float confidence,
      boolean faceDetected,
      String feedback) {
    return FeedbackResponse.builder()
        .sessionId(sessionId)
        .timestamp(timestamp)
        .label(label)
        .confidence(confidence)
        .faceDetected(faceDetected)
        .feedback(feedback)
        .build();
  }

  private org.assertj.core.data.Offset<Double> withinOffset() {
    return org.assertj.core.data.Offset.offset(0.0001);
  }
}
