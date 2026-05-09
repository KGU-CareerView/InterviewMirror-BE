package com.interviewmirror.domain.feedback.service;

import com.interviewmirror.domain.feedback.dto.FeedbackResponse;
import com.interviewmirror.domain.feedback.dto.FeedbackSummaryResponse;
import com.interviewmirror.domain.feedback.entity.FeedbackResult;
import com.interviewmirror.domain.feedback.repository.FeedbackBufferRepository;
import com.interviewmirror.domain.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackAggregationService {

    private static final String STABLE = "stable_confident";
    private static final String NERVOUS = "nervous_anxious";
    private static final String NEUTRAL = "neutral";
    private static final String UNKNOWN = "Unknown";

    private final FeedbackBufferRepository feedbackBufferRepository;
    private final FeedbackRepository feedbackRepository;

    @Transactional
    public FeedbackSummaryResponse aggregateAndSave(String sessionId) {
        return feedbackRepository.findBySessionId(sessionId)
                .map(this::toSummaryResponse)
                .orElseGet(() -> createSummary(sessionId));
    }

    private FeedbackSummaryResponse createSummary(String sessionId) {
        List<FeedbackResponse> responses = feedbackBufferRepository.findAll(sessionId);

        FeedbackResult result = feedbackRepository.save(buildResult(sessionId, responses));
        feedbackBufferRepository.delete(sessionId);

        return toSummaryResponse(result);
    }

    private FeedbackResult buildResult(String sessionId, List<FeedbackResponse> responses) {
        int totalFrames = responses.size();
        LocalDateTime endedAt = LocalDateTime.now();

        if (responses.isEmpty()) {
            return FeedbackResult.builder()
                    .sessionId(sessionId)
                    .totalFrames(0)
                    .dominantLabel(UNKNOWN)
                    .averageConfidence(0.0)
                    .stableCount(0)
                    .nervousCount(0)
                    .neutralCount(0)
                    .faceDetectedCount(0)
                    .endedAt(endedAt)
                    .build();
        }

        return FeedbackResult.builder()
                .sessionId(sessionId)
                .totalFrames(totalFrames)
                .dominantLabel(resolveDominantLabel(responses))
                .averageConfidence(average(responses, FeedbackResponse::getConfidence))
                .stableCount(countLabel(responses, STABLE))
                .nervousCount(countLabel(responses, NERVOUS))
                .neutralCount(countLabel(responses, NEUTRAL))
                .faceDetectedCount(countFaceDetected(responses))
                .latestFeedback(resolveLatestFeedback(responses))
                .startedAt(toDateTime(responses.stream()
                        .mapToLong(FeedbackResponse::getTimestamp)
                        .min()
                        .orElse(0L)))
                .endedAt(endedAt)
                .build();
    }

    private String resolveDominantLabel(List<FeedbackResponse> responses) {
        Map<String, Long> counts = responses.stream()
                .map(response -> labelOrUnknown(response.getLabel()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return counts.entrySet()
                .stream()
                .max(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(UNKNOWN);
    }

    private int countLabel(List<FeedbackResponse> responses, String label) {
        return (int) responses.stream()
                .filter(response -> label.equalsIgnoreCase(response.getLabel()))
                .count();
    }

    private int countFaceDetected(List<FeedbackResponse> responses) {
        return (int) responses.stream()
                .filter(FeedbackResponse::isFaceDetected)
                .count();
    }

    private String resolveLatestFeedback(List<FeedbackResponse> responses) {
        return responses.stream()
                .max(Comparator.comparingLong(FeedbackResponse::getTimestamp))
                .map(FeedbackResponse::getFeedback)
                .orElse(null);
    }

    private String labelOrUnknown(String label) {
        return label == null || label.isBlank() ? UNKNOWN : label;
    }

    private double average(List<FeedbackResponse> responses, Function<FeedbackResponse, Float> extractor) {
        return responses.stream()
                .map(extractor)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);
    }

    private LocalDateTime toDateTime(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    }

    private FeedbackSummaryResponse toSummaryResponse(FeedbackResult result) {
        return FeedbackSummaryResponse.builder()
                .id(result.getId())
                .sessionId(result.getSessionId())
                .totalFrames(result.getTotalFrames())
                .dominantLabel(result.getDominantLabel())
                .averageConfidence(result.getAverageConfidence())
                .stableCount(result.getStableCount())
                .nervousCount(result.getNervousCount())
                .neutralCount(result.getNeutralCount())
                .faceDetectedCount(result.getFaceDetectedCount())
                .latestFeedback(result.getLatestFeedback())
                .startedAt(result.getStartedAt())
                .endedAt(result.getEndedAt())
                .createdAt(result.getCreatedAt())
                .build();
    }
}
