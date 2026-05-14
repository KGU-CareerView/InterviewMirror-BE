package com.interviewmirror.interview.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InterviewReportResponse {
  private Long sessionId;
  private String videoUrl; // S3에 저장된 면접 녹화 영상 링크
  private String emotionGraphJson; // 세션 전체 감정 변화 그래프 데이터
  private Integer totalScore; // 종합 점수
  private String feedback; // 종합 피드백
  private String strengths; // 강점
  private String weaknesses; // 보완점
  private String aiAnalysisJson; // 기타 상세 분석
}
