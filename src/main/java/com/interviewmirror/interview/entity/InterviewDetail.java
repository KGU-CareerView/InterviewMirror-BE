package com.interviewmirror.interview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewDetail {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id")
  private InterviewResult interviewResult;

  private Long qId;

  @Column(name = "question_text", columnDefinition = "TEXT")
  private String question;

  @Column(name = "answer_text", columnDefinition = "TEXT")
  private String answer;

  @Lob
  @Column(columnDefinition = "TEXT")
  private String emotionResult;

  private Integer responseTimeSeconds;

  @Lob
  @Column(name = "audio_summary_json", columnDefinition = "TEXT")
  private String audioSummaryJson;

  @Column(name = "audio_score")
  private Integer audioScore;

  @Column(name = "total_score")
  private Integer totalScore;

  @Column(name = "content_score")
  private Integer contentScore;

  @Column(name = "expression_score")
  private Integer expressionScore;

  @Lob
  @Column(name = "feedback", columnDefinition = "TEXT")
  private String feedback;

  @Lob
  @Column(name = "content_feedback", columnDefinition = "TEXT")
  private String contentFeedback;

  @Lob
  @Column(name = "voice_feedback", columnDefinition = "TEXT")
  private String voiceFeedback;

  @Lob
  @Column(name = "expression_feedback", columnDefinition = "TEXT")
  private String expressionFeedback;
}
