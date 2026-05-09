package com.interviewmirror.domain.feedback.client;

import com.interviewmirror.grpc.proto.AnalysisResponse;
import com.interviewmirror.grpc.proto.FeatureRequest;
import com.interviewmirror.grpc.proto.InterviewAIServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class EmotionAnalysisClient {

    @GrpcClient("ai-server")
    private InterviewAIServiceGrpc.InterviewAIServiceStub interviewAIServiceStub;

    public StreamObserver<FeatureRequest> startAnalysisStream(StreamObserver<AnalysisResponse> responseObserver) {
        return interviewAIServiceStub.analyzeFrameStream(responseObserver);
    }
}
