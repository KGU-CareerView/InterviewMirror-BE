package com.interviewmirror.interview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSetting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long settingId;

  private Long userId; // 사용자와의 연관관계 (필요 시 @ManyToOne으로 변경 가능)

  @Column(length = 50)
  private String category; // 분야

  @Column(length = 20)
  private String interviewType; // 인성, 직무, 종합

  @Column(length = 20)
  private String difficulty; // 난이도

  private Integer questionCount; // 질문 개수

  private Integer timePerQuestion; // 질문당 시간(초)

  @Lob // 텍스트가 길어질 수 있으므로 Lob 사용
  private String resumeContent; // 자소서 / JD 원문 또는 S3 URL
}
