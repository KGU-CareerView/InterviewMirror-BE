package com.interviewmirror.domain.interview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_Details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewDetail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long numbering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessionID")
    private InterviewResult interviewResult;

    private Long qId;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;
}