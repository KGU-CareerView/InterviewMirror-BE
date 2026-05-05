package com.interviewmirror.config;

import org.springframework.stereotype.Service;

@Service
public class AiGrpcClient {
    // private final AiServiceGrpc.AiServiceBlockingStub blockingStub; // 실제 gRPC Stub

    public String generateNextQuestion(Long sessionId, String answer) {
        // 실제 동작: grpc 요청 및 응답 반환
        // return blockingStub.generateQuestion(QuestionRequest.newBuilder().setAnswer(answer).build()).getQuestion();
        return "AI가 생성한 다음 질문입니다. (Mock)";
    }

    public String analyzeEmotion(Long sessionId, Object facialData) {
        // 안면 데이터를 AI 서버로 넘겨 분석값(긴장, 평온 등) 반환
        return "Nervous"; // Mock
    }
}