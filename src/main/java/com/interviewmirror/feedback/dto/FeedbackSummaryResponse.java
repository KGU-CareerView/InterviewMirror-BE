package com.interviewmirror.domain.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackSummaryResponse {

    private Long id;
    private String sessionId;
    private int totalFrames;
    private String dominantLabel;
    private double averageConfidence;
    private int stableCount;
    private int nervousCount;
    private int neutralCount;
    private int faceDetectedCount;
    private String latestFeedback;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
}
