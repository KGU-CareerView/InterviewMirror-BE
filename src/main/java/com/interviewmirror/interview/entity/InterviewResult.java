package com.interviewmirror.interview.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Entity
@Table(name = "interview_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "session_id")
  private Long sessionId;

  @Builder.Default
  @OneToMany(mappedBy = "interviewResult", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<InterviewDetail> details = new ArrayList<>();

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "session_state", length = 20)
  private String sessionState;

  @Column(name = "video_url", length = 255)
  private String videoUrl;

  @Column(name = "created_at")
  private LocalDateTime createTime;

  @Lob
  @Column(name = "emotion_graph_json", columnDefinition = "LONGTEXT")
  private String emotionGraph;
}
