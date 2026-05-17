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
}
