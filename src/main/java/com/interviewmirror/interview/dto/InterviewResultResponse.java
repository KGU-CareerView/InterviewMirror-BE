package com.interviewmirror.interview.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewResultResponse {

  // InterviewResult에서 가져올 데이터
  private Long sessionId;
  private String videoUrl;
  private LocalDateTime createTime;
  private String emotionGraph;

  // InterviewDetail에서 가져올 Q&A 리스트 데이터
  private List<DetailDto> details;

  // 내부 정적(static) 클래스로 상세 문답 DTO 정의
  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DetailDto {
    private Long qId;
    private String question;
    private String answer; // STT 변환된 답변 (대본)
    private String emotionResult;
    private Integer responseTimeSeconds;

    // 질문별 점수
    private Integer totalScore;
    private Integer contentScore;
    private Integer voiceScore;
    private Integer expressionScore;

    // 질문별 피드백
    private String feedback;
    private String contentFeedback;
    private String voiceFeedback;
    private String expressionFeedback;
  }
}
