package com.interviewmirror.realtime.client;

import com.interviewmirror.grpc.proto.AnalysisResponse;
import com.interviewmirror.grpc.proto.FeatureRequest;
import com.interviewmirror.grpc.proto.FinalReportRequest;
import com.interviewmirror.grpc.proto.FinalReportResponse;
import com.interviewmirror.grpc.proto.FollowUpQuestionGenerateRequest;
import com.interviewmirror.grpc.proto.FollowUpQuestionGenerateResponse;
import com.interviewmirror.grpc.proto.InitialQuestionGenerateRequest;
import com.interviewmirror.grpc.proto.InitialQuestionGenerateResponse;
import com.interviewmirror.grpc.proto.InterviewAIServiceGrpc;
import com.interviewmirror.grpc.proto.VoiceToneAnalysisRequest;
import com.interviewmirror.grpc.proto.VoiceToneAnalysisResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiGrpcClient {

  @GrpcClient("ai-server")
  private InterviewAIServiceGrpc.InterviewAIServiceBlockingStub blockingStub;

  @GrpcClient("ai-server")
  private InterviewAIServiceGrpc.InterviewAIServiceStub asyncStub;

  public StreamObserver<FeatureRequest> startAnalysisStream(
      StreamObserver<AnalysisResponse> responseObserver) {
    return asyncStub.analyzeFrameStream(responseObserver);
  }

  public InitialQuestionGenerateResponse requestInitialQuestions(
      Long sessionId,
      Long userId,
      String category,
      String interviewType,
      String difficulty,
      int questionCount,
      int timePerQuestion,
      String resumeContent) {
    InitialQuestionGenerateRequest request =
        InitialQuestionGenerateRequest.newBuilder()
            .setSessionId(String.valueOf(sessionId))
            .setUserId(String.valueOf(userId))
            .setCategory(valueOrEmpty(category))
            .setInterviewType(valueOrEmpty(interviewType))
            .setDifficulty(valueOrEmpty(difficulty))
            .setQuestionCount(questionCount)
            .setTimePerQuestion(timePerQuestion)
            .setResumeText(valueOrEmpty(resumeContent))
            .setLanguage("ko")
            .build();

    log.info(
        "AI gRPC request: method=GenerateInitialQuestions sessionId={} userId={} category={} interviewType={} difficulty={} questionCount={} timePerQuestion={} resumeLength={}",
        request.getSessionId(),
        request.getUserId(),
        request.getCategory(),
        request.getInterviewType(),
        request.getDifficulty(),
        request.getQuestionCount(),
        request.getTimePerQuestion(),
        request.getResumeText().length());
    InitialQuestionGenerateResponse response = blockingStub.generateInitialQuestions(request);
    log.info(
        "AI gRPC response: method=GenerateInitialQuestions sessionId={} userId={} questionCount={}",
        response.getSessionId(),
        response.getUserId(),
        response.getQuestionsCount());
    return response;
  }

  public String generateFollowUpQuestion(Long sessionId, String previousQuestion, String answer) {
    FollowUpQuestionGenerateRequest request =
        FollowUpQuestionGenerateRequest.newBuilder()
            .setSessionId(String.valueOf(sessionId))
            .setPreviousQuestion(valueOrEmpty(previousQuestion))
            .setAnswer(valueOrEmpty(answer))
            .setLanguage("ko")
            .build();

    log.info(
        "AI gRPC request: method=GenerateFollowUpQuestion sessionId={} previousQuestionLength={} answerLength={}",
        request.getSessionId(),
        request.getPreviousQuestion().length(),
        request.getAnswer().length());
    FollowUpQuestionGenerateResponse response = blockingStub.generateFollowUpQuestion(request);
    String question = response.getQuestion().getQuestion();
    log.info(
        "AI gRPC response: method=GenerateFollowUpQuestion sessionId={} userId={} questionPreview={}",
        response.getSessionId(),
        response.getUserId(),
        preview(question));
    return question;
  }

  public void generateFinalReportAsync(
      FinalReportRequest request, StreamObserver<FinalReportResponse> observer) {
    log.info(
        "AI gRPC request: method=GenerateFinalReport sessionId={} questionCount={}",
        request.getSessionId(),
        request.getQuestionResultsCount());
    asyncStub.generateFinalReport(request, observer);
  }

  public VoiceToneAnalysisResponse analyzeVoiceTone(VoiceToneAnalysisRequest request) {
    log.info(
        "AI gRPC request: method=AnalyzeVoiceTone sessionId={} questionIndex={} zcrSampleCount={}",
        request.getSessionId(),
        request.getQuestionIndex(),
        request.getZcrSamplesCount());
    VoiceToneAnalysisResponse response = blockingStub.analyzeVoiceTone(request);
    log.info(
        "AI gRPC response: method=AnalyzeVoiceTone sessionId={} overallScore={}",
        response.getSessionId(),
        response.getOverallStabilityScore());
    return response;
  }

  public java.util.concurrent.CompletableFuture<VoiceToneAnalysisResponse> analyzeVoiceToneAsync(
      VoiceToneAnalysisRequest request) {
    log.info(
        "AI gRPC request: method=AnalyzeVoiceTone(async) sessionId={} questionIndex={} zcrSampleCount={}",
        request.getSessionId(),
        request.getQuestionIndex(),
        request.getZcrSamplesCount());
    java.util.concurrent.CompletableFuture<VoiceToneAnalysisResponse> future =
        new java.util.concurrent.CompletableFuture<>();
    asyncStub.analyzeVoiceTone(
        request,
        new io.grpc.stub.StreamObserver<VoiceToneAnalysisResponse>() {
          @Override
          public void onNext(VoiceToneAnalysisResponse response) {
            log.info(
                "AI gRPC response: method=AnalyzeVoiceTone(async) sessionId={} overallScore={}",
                response.getSessionId(),
                response.getOverallStabilityScore());
            future.complete(response);
          }

          @Override
          public void onError(Throwable t) {
            log.warn(
                "AI gRPC error: method=AnalyzeVoiceTone(async) sessionId={} error={}",
                request.getSessionId(),
                t.getMessage());
            future.completeExceptionally(t);
          }

          @Override
          public void onCompleted() {}
        });
    return future;
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private String preview(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.length() <= 80 ? value : value.substring(0, 80) + "...";
  }
}
