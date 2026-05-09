package com.interviewmirror.config;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import com.interviewmirror.grpc.AiServiceGrpc; // proto 파일 컴파일로 생성된 클래스
import com.interviewmirror.grpc.QuestionRequest;
import com.interviewmirror.grpc.QuestionResponse;
import com.interviewmirror.grpc.EmotionRequest;
import com.interviewmirror.grpc.EmotionResponse;
import com.interviewmirror.grpc.InitialQuestionRequest;
import com.interviewmirror.grpc.InitialQuestionResponse;
import com.interviewmirror.grpc.TipRequest;
import com.interviewmirror.grpc.TipResponse;

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

    public List<String> requestInitialQuestions(String category, String type, String difficulty, int count, String resume) {
        log.info("[gRPC] AI 서버로 초기 질문 생성 요청 (분야: {}, 유형: {}, 개수: {})", category, type, count);

        // ProtoBuf 객체 생성 (null 방지를 위해 삼항 연산자 사용)
        InitialQuestionRequest request = InitialQuestionRequest.newBuilder()
                .setCategory(category != null ? category : "")
                .setInterviewType(type != null ? type : "")
                .setDifficulty(difficulty != null ? difficulty : "")
                .setQuestionCount(count)
                .setResumeContent(resume != null ? resume : "")
                .build();

        // gRPC 서버 호출
        InitialQuestionResponse response = aiServiceStub.generateInitialQuestions(request);

        log.info("[gRPC] 초기 질문 생성 완료: {}개 반환됨", response.getQuestionsCount());

        // proto의 repeated string은 Java의 List<String>으로 매핑됩니다.
        return response.getQuestionsList();
    }

    /**
     * 2. 특정 질문에 대한 답변 팁(요령) 생성 요청
     * @return AI가 생성한 팁 문자열
     */
    public String requestTipGeneration(String question, String resumeContent) {
        log.info("[gRPC] AI 서버로 답변 팁 생성 요청 (대상 질문: {})", question);

        TipRequest request = TipRequest.newBuilder()
                .setQuestion(question != null ? question : "")
                .setResumeContent(resumeContent != null ? resumeContent : "")
                .build();

        TipResponse response = aiServiceStub.generateTip(request);

        return response.getTip();
    }




    /*
    public String generateNextQuestion(Long sessionId, String answer) {
        return "AI가 생성한 다음 질문입니다. (Mock)";
    }

    public String analyzeEmotion(Long sessionId, Object facialData) {
        return "Nervous"; // Mock
    }*/
}