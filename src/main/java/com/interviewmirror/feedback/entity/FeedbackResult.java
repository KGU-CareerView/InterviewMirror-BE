package com.interviewmirror.domain.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feedback_results")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, unique = true, length = 100)
  private String sessionId;

  @Column(name = "total_frames", nullable = false)
  private int totalFrames;

  @Column(name = "dominant_label", nullable = false, length = 50)
  private String dominantLabel;

  @Column(name = "average_confidence", nullable = false)
  private double averageConfidence;

  @Column(name = "stable_count", nullable = false)
  private int stableCount;

  @Column(name = "nervous_count", nullable = false)
  private int nervousCount;

  @Column(name = "neutral_count", nullable = false)
  private int neutralCount;

  @Column(name = "face_detected_count", nullable = false)
  private int faceDetectedCount;

  @Column(name = "latest_feedback", length = 1000)
  private String latestFeedback;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "ended_at", nullable = false)
  private LocalDateTime endedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}
