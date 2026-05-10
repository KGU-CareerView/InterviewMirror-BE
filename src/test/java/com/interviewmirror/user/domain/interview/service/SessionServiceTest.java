package com.interviewmirror.user.domain.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.RabbitMQProducer;
import com.interviewmirror.domain.interview.entity.InterviewDetail;
import com.interviewmirror.domain.interview.entity.InterviewResult;
import com.interviewmirror.domain.interview.repository.InterviewDetailRepository;
import com.interviewmirror.domain.interview.repository.InterviewResultRepository;
import com.interviewmirror.domain.interview.service.RedisSessionService;
import com.interviewmirror.domain.interview.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @InjectMocks
    private SessionService sessionService;

    @Mock
    private InterviewResultRepository resultRepository;

    @Mock
    private InterviewDetailRepository detailRepository;

    @Mock
    private RedisSessionService redisSessionService;

    @Mock
    private AiGrpcClient aiGrpcClient;

    @Mock
    private RabbitMQProducer rabbitMQProducer;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    // 실제 동작하는 ObjectMapper를 Spy로 주입
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("면접 세션 생성 테스트 - DB 저장 후 ID 반환 및 Redis 상태 업데이트 검증")
    void createSessionTest() {
        // given
        Long userId = 1L;
        InterviewResult mockResult = InterviewResult.builder()
                .sessionId(100L)
                .userId(userId)
                .sessionState("pause")
                .createTime(LocalDateTime.now())
                .build();

        given(resultRepository.save(any(InterviewResult.class))).willReturn(mockResult);

        // when
        Long sessionId = sessionService.createSession(userId);

        // then
        verify(redisSessionService).updateSessionState(100L, "pause");
        assert sessionId == 100L;
    }

    @Test
    @DisplayName("면접 종료 테스트 (END) - RabbitMQ 리포트 요청 및 Redis 데이터 초기화 검증")
    void changeStateEndTest() {
        // given
        Long sessionId = 1L;
        InterviewResult mockResult = InterviewResult.builder()
                .sessionId(sessionId)
                .sessionState("pause")
                .build();

        given(resultRepository.findById(sessionId)).willReturn(Optional.of(mockResult));

        // 파싱 가능한 올바른 형식의 JSON 문자열 주입
        String validJson = "{\"quizID\":1,\"question\":\"Q\",\"answer\":\"A\"}";
        List<String> mockQaList = List.of(validJson);
        given(redisSessionService.getQaList(sessionId)).willReturn(mockQaList);

        // when
        sessionService.changeState(sessionId, "end");

        // then
        verify(redisSessionService).updateSessionState(sessionId, "end");
        verify(detailRepository, times(1)).save(any(InterviewDetail.class));
        verify(redisSessionService).clearQaList(sessionId);
        verify(rabbitMQProducer).sendReportRequest(sessionId);
    }

    @Test
    @DisplayName("답변 처리 및 AI 질문 생성 테스트 - 정상 흐름 검증 (Redis, gRPC, WebSocket 연동)")
    void processAnswerAndGenerateQuestionTest() throws Exception {
        // given
        Long sessionId = 1L;
        String answer = "이것은 답변입니다.";

        given(redisSessionService.getLastQuestion(sessionId)).willReturn("이전 질문입니다.");
        given(redisSessionService.lockQuestionGeneration(sessionId)).willReturn(true);
        given(aiGrpcClient.generateNextQuestion(sessionId, answer)).willReturn("다음 질문입니다.");

        // when
        sessionService.processAnswerAndGenerateQuestion(sessionId, answer);

        // then
        verify(redisSessionService).addQaToRedis(eq(sessionId), anyString());
        verify(aiGrpcClient).generateNextQuestion(sessionId, answer);
        verify(redisSessionService).setLastQuestion(sessionId, "다음 질문입니다.");
        verify(messagingTemplate).convertAndSend(eq("/topic/session/1/question"), anyMap());
    }
}