package com.interviewmirror.realtime.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.grpc.proto.InitialQuestionGenerateResponse;
import com.interviewmirror.grpc.proto.QuestionItem;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.realtime.client.AiGrpcClient;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeQuestionGenerationServiceTest {

  @Mock private AiGrpcClient aiGrpcClient;

  @Mock private InterviewResultRepository resultRepository;

  @Mock private RedisSessionService redisSessionService;

  @Mock private RealtimeMessagePublisher realtimeMessagePublisher;

  @InjectMocks private RealtimeQuestionGenerationService questionGenerationService;

  @Test
  @DisplayName("초기 질문 생성 성공 시 첫 질문 저장 후 준비 완료 이벤트를 발행한다.")
  void generateInitialQuestions_Success() {
    Long sessionId = 1L;
    Long userId = 10L;
    InterviewSettingRequest request =
        new InterviewSettingRequest("BACKEND", "TECH", "NORMAL", 3, 30, "resume");
    InitialQuestionGenerateResponse response =
        InitialQuestionGenerateResponse.newBuilder()
            .addQuestions(QuestionItem.newBuilder().setIndex(1).setQuestion("첫 질문").build())
            .addQuestions(QuestionItem.newBuilder().setIndex(2).setQuestion("두 번째 질문").build())
            .build();

    when(redisSessionService.lockQuestionGeneration(sessionId)).thenReturn(true);
    when(aiGrpcClient.requestInitialQuestions(
            sessionId, userId, "BACKEND", "TECH", "NORMAL", 3, 30, "resume"))
        .thenReturn(response);
    InterviewResult result = InterviewResult.builder().sessionId(sessionId).build();
    when(resultRepository.findById(sessionId)).thenReturn(Optional.of(result));

    questionGenerationService.generateInitialQuestions(sessionId, userId, request);

    verify(redisSessionService).setLastQuestion(sessionId, "첫 질문");
    verify(resultRepository).save(result);
    verify(redisSessionService)
        .updateSessionState(sessionId, InterviewSessionState.IN_PROGRESS.name());
    verify(realtimeMessagePublisher)
        .publishInitialQuestionsReady(eq(sessionId), eq("첫 질문"), anyList());
    verify(redisSessionService).unlockQuestionGeneration(sessionId);
  }

  @Test
  @DisplayName("초기 질문 생성 락 획득 실패 시 중복 처리 경고를 발행한다.")
  void generateInitialQuestions_LockFailed() {
    Long sessionId = 1L;
    InterviewSettingRequest request =
        new InterviewSettingRequest("BACKEND", "TECH", "NORMAL", 3, 30, "resume");

    when(redisSessionService.lockQuestionGeneration(sessionId)).thenReturn(false);

    questionGenerationService.generateInitialQuestions(sessionId, 10L, request);

    verify(realtimeMessagePublisher).publishQuestionProcessingWarning(sessionId);
    verifyNoInteractions(aiGrpcClient);
    verifyNoInteractions(resultRepository);
  }

  @Test
  @DisplayName("AI 서버가 빈 질문 목록을 응답하면 실패 이벤트를 발행한다.")
  void generateInitialQuestions_EmptyResponse() {
    Long sessionId = 1L;
    Long userId = 10L;
    InterviewSettingRequest request =
        new InterviewSettingRequest("BACKEND", "TECH", "NORMAL", 3, 30, "resume");

    when(redisSessionService.lockQuestionGeneration(sessionId)).thenReturn(true);
    when(aiGrpcClient.requestInitialQuestions(
            sessionId, userId, "BACKEND", "TECH", "NORMAL", 3, 30, "resume"))
        .thenReturn(InitialQuestionGenerateResponse.getDefaultInstance());

    questionGenerationService.generateInitialQuestions(sessionId, userId, request);

    verify(realtimeMessagePublisher).publishSessionError(sessionId, ErrorCode.AI_RESPONSE_FAILED);
    verify(redisSessionService).unlockQuestionGeneration(sessionId);
  }

  @Test
  @DisplayName("꼬리 질문 생성 성공 시 다음 질문을 저장하고 이벤트를 발행한다.")
  void generateFollowUpQuestion_Success() {
    Long sessionId = 1L;
    String previousQuestion = "이전 질문";
    String answer = "사용자 답변";

    when(redisSessionService.lockQuestionGeneration(sessionId)).thenReturn(true);
    when(aiGrpcClient.generateFollowUpQuestion(sessionId, previousQuestion, answer))
        .thenReturn("다음 질문");

    questionGenerationService.generateFollowUpQuestion(sessionId, previousQuestion, answer);

    verify(redisSessionService).setLastQuestion(sessionId, "다음 질문");
    verify(realtimeMessagePublisher).publishNextQuestion(sessionId, "다음 질문");
    verify(redisSessionService).unlockQuestionGeneration(sessionId);
  }

  @Test
  @DisplayName("꼬리 질문 생성 락 획득 실패 시 중복 처리 경고를 발행한다.")
  void generateFollowUpQuestion_LockFailed() {
    Long sessionId = 1L;

    when(redisSessionService.lockQuestionGeneration(sessionId)).thenReturn(false);

    questionGenerationService.generateFollowUpQuestion(sessionId, "이전 질문", "사용자 답변");

    verify(realtimeMessagePublisher).publishQuestionProcessingWarning(sessionId);
    verifyNoInteractions(aiGrpcClient);
  }
}
