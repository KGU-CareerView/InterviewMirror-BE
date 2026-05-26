package com.interviewmirror.interview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_Reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewReport {

  @Id private Long sessionId; // InterviewResult의 sessionId를 그대로 사용

  @OneToOne
  @MapsId // InterviewResult의 PK를 이 엔티티의 PK로 매핑
  @JoinColumn(name = "session_id")
  private InterviewResult interviewResult;

  @PrePersist
  private void ensureSessionIdFromRelationship() {
    if (sessionId == null && interviewResult != null) {
      sessionId = interviewResult.getSessionId();
    }
  }

  private Integer totalScore; // 종합 점수

  @Lob
  @Column(columnDefinition = "TEXT")
  private String feedback; // 전체적인 종합 피드백

  @Lob
  @Column(columnDefinition = "TEXT")
  private String strengths; // 사용자의 강점 분석

  @Lob
  @Column(columnDefinition = "TEXT")
  private String weaknesses; // 사용자의 보완점 분석

  @Lob
  @Column(columnDefinition = "TEXT")
  private String aiAnalysisJson; // 기타 상세 AI 분석 지표 (JSON 형태)
}
