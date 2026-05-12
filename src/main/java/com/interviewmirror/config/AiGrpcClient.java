package com.interviewmirror.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiGrpcClient {

  // AI 통신이 구현되기 전까지 사용하는 가짜(Mock) 메서드들입니다.

  public String generateNextQuestion(Long sessionId, String answer) {
    log.info("[MOCK] 다음 질문 생성 요청 - SessionID: {}, Answer: {}", sessionId, answer);
    return "AI가 생성한 다음 질문입니다. (Mock)";
  }

  public String analyzeEmotion(Long sessionId, String faceData) {
    log.info("[MOCK] 감정 분석 요청 - SessionID: {}", sessionId);
    return "Nervous";
  }

  // InterviewPreparationService에서 쓰이는 초기 질문 요청 (Mock)
  public void requestInitialQuestions(
      String category, String type, String difficulty, int count, String resume) {
    log.info("[MOCK] 초기 질문 생성 요청을 보냈습니다. (카테고리: {})", category);
  }

  // InterviewPreparationService에서 쓰이는 답변 팁 생성 (Mock)
  public String requestTipGeneration(String question, String resumeContent) {
    log.info("[MOCK] 답변 팁 생성 요청 - Question: {}", question);
    return "이것은 임시로 생성된 모범 답변 팁입니다.";
  }
}
