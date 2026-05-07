package com.interviewmirror.config;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import com.interviewmirror.grpc.AiServiceGrpc; // proto 파일 컴파일로 생성된 클래스
import com.interviewmirror.grpc.QuestionRequest;
import com.interviewmirror.grpc.QuestionResponse;
import com.interviewmirror.grpc.EmotionRequest;
import com.interviewmirror.grpc.EmotionResponse;

@Slf4j
@Service
public class AiGrpcClient {
    //application.yml에 설정한 gRPC 서버(ai-server)와 연결된 Stub 주입
    @GrpcClient("ai-server")
    private AiServiceGrpc.AiServiceBlockingStub aiServiceStub;

    public String generateNextQuestion(Long sessionId, String answer) {
        log.info("[gRPC] AI 서버로 꼬리 질문 생성 요청 (세션: {})", sessionId);

        // 실제 gRPC 호출 로직
        QuestionRequest request = QuestionRequest.newBuilder()
                .setSessionId(sessionId)
                .setAnswer(answer)
                .build();

        QuestionResponse response = aiServiceStub.generateQuestion(request);
        return response.getNextQuestion();
        //
    }

    public String analyzeEmotion(Long sessionId, String faceData) {
        // 로그 메시지 수정
        log.info("[gRPC] AI 서버로 감정 분석 요청 (세션: {})", sessionId);

        // QuestionRequest가 아닌 EmotionRequest 사용
        EmotionRequest request = EmotionRequest.newBuilder()
                .setSessionId(sessionId)
                .setFaceData(faceData)
                .build();

        // generateQuestion이 아닌 analyzeEmotion 메서드 호출
        EmotionResponse response = aiServiceStub.analyzeEmotion(request);
        return response.getEmotionResult();
    }




    /*
    public String generateNextQuestion(Long sessionId, String answer) {
        return "AI가 생성한 다음 질문입니다. (Mock)";
    }

    public String analyzeEmotion(Long sessionId, Object facialData) {
        return "Nervous"; // Mock
    }*/
}