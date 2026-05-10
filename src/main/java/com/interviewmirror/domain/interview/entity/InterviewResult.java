package com.interviewmirror.domain.interview.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Entity
@Table(name = "interview_Results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "sessionID")
  private Long sessionId; // 수동 할당 (Redis에서 발급한 ID 사용)

  @Builder.Default
  @OneToMany(mappedBy = "interviewResult", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<InterviewDetail> details = new ArrayList<>();

  private Long userId;

  @Column(length = 20)
  private String sessionState; // START, PAUSE, RESUME, END

  @Column(length = 255)
  private String videoUrl;

  private LocalDateTime createTime;

  @Lob // JSON 형태로 저장될 수 있음
  private String emotionGraph;
}
