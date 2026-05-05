package com.interviewmirror.user.domain.interview.service;

import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.RabbitMQProducer;
import com.interviewmirror.domain.interview.entity.InterviewDetail;
import com.interviewmirror.domain.interview.entity.InterviewResult;
import com.interviewmirror.domain.interview.repository.InterviewDetailRepository;
import com.interviewmirror.domain.interview.repository.InterviewResultRepository;
import com.interviewmirror.domain.interview.service.SessionService;
import com.interviewmirror.domain.interview.service.RedisSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @InjectMocks
    private SessionService sessionService; // 가짜 객체들을 주입받을 실제 테스트 대상

    @Mock
    private InterviewResultRepository resultRepository; // 가짜(Mock) DB 역할
    @Mock
    private InterviewDetailRepository detailRepository;
    @Mock
    private RabbitMQProducer rabbitMQProducer;
    @Mock
    private AiGrpcClient aiGrpcClient;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RedisSessionService redisSessionService; // 가짜(Mock) Redis 역할

    @Test
    @DisplayName("면접 세션 생성 테스트 - DB 저장 후 ID 반환 및 Redis 상태 업데이트 검증")
    void createSessionTest() {
        // given (준비 단계: 가짜 객체들이 어떻게 행동할지 시나리오를 짭니다)
        Long expectedSessionId = 100L;
        InterviewResult fakeSavedResult = InterviewResult.builder()
                .sessionId(expectedSessionId)
                .sessionState("대기중")
                .createTime(LocalDateTime.now())
                .build();

        // Repository에 어떤 객체가 들어오든 save를 호출하면 가짜 객체를 반환해라!
        when(resultRepository.save(any(InterviewResult.class))).thenReturn(fakeSavedResult);

        // when (실행 단계: 실제로 서비스 로직을 실행합니다)
        Long actualSessionId = sessionService.createSession();

        // then (검증 단계: 결과가 예상대로 나왔는지 확인합니다)
        assertEquals(expectedSessionId, actualSessionId, "발급된 세션 ID가 일치해야 합니다.");

        // Repository의 save 메서드가 딱 1번 호출되었는지 검증
        verify(resultRepository, times(1)).save(any(InterviewResult.class));

        // Redis에 상태 업데이트("대기중")가 정상적으로 딱 1번 호출되었는지 검증
        verify(redisSessionService, times(1)).updateSessionState(expectedSessionId, "대기중");
    }


    @Test
    @DisplayName("면접 상태 변경 테스트 (START) - DB 및 Redis 상태 업데이트 검증")
    void changeStateStartTest() {
        // given: 1번 세션이 DB에 존재한다고 가정
        Long sessionId = 1L;
        InterviewResult mockResult = InterviewResult.builder()
                .sessionId(sessionId)
                .sessionState("INIT")
                .build();

        when(resultRepository.findById(sessionId)).thenReturn(java.util.Optional.of(mockResult));

        // when: 상태를 "START"로 변경
        sessionService.changeState(sessionId, "START");

        // then: Redis 업데이트가 호출되었고, DB 엔티티의 상태값이 변경되었는지 확인
        verify(redisSessionService, times(1)).updateSessionState(sessionId, "START");
        assertEquals("START", mockResult.getSessionState(), "상태가 START로 변경되어야 합니다.");
    }

    @Test
    @DisplayName("면접 종료 테스트 (END) - RabbitMQ 리포트 요청 및 Redis 데이터 초기화 검증")
    void changeStateEndTest() {
        // given: 1번 세션에 대한 종료 요청
        Long sessionId = 1L;
        InterviewResult mockResult = InterviewResult.builder()
                .sessionId(sessionId)
                .sessionState("진행중")
                .build();

        when(resultRepository.findById(sessionId)).thenReturn(java.util.Optional.of(mockResult));

        // Redis에 저장된 가상의 문답 데이터 1개 셋팅
        String mockQaJson = "{\"quizID\":12345, \"question\":\"질문입니다\", \"answer\":\"답변입니다\"}";
        when(redisSessionService.getQaList(sessionId)).thenReturn(List.of(mockQaJson));

        // when: 상태를 "END"로 변경
        sessionService.changeState(sessionId, "END");

        // then
        verify(redisSessionService, times(1)).updateSessionState(sessionId, "END"); // 1. 상태 업데이트 확인
        verify(detailRepository, times(1)).save(any(InterviewDetail.class));        // 2. DB에 상세 문답 저장 확인
        verify(redisSessionService, times(1)).clearQaList(sessionId);               // 3. Redis 문답 초기화 확인
        verify(rabbitMQProducer, times(1)).sendReportRequest(sessionId);            // 4. RabbitMQ 리포트 생성 요청 확인
    }

    @Test
    @DisplayName("비디오 URL 저장 테스트 - 정상 저장 검증")
    void saveVideoUrlTest() {
        // given
        Long sessionId = 1L;
        String videoUrl = "https://s3.aws.com/my-video.mp4";
        InterviewResult mockResult = InterviewResult.builder().sessionId(sessionId).build();

        when(resultRepository.findById(sessionId)).thenReturn(java.util.Optional.of(mockResult));

        // when
        sessionService.saveVideoUrl(sessionId, videoUrl);

        // then
        assertEquals(videoUrl, mockResult.getVideoUrl(), "비디오 URL이 엔티티에 세팅되어야 합니다.");
    }

    @Test
    @DisplayName("답변 처리 및 AI 질문 생성 테스트 - 정상 흐름 검증 (Redis, gRPC, WebSocket 연동)")
    void processAnswerAndGenerateQuestionTest() throws Exception {
        // given: 가상의 세션 ID, 답변, 이전 질문, AI가 새로 만들 질문 세팅
        Long sessionId = 1L;
        String answer = "저는 스프링 부트를 좋아합니다.";
        String lastQuestion = "지원자님의 장점은 무엇인가요?";
        String nextQuestion = "스프링 부트의 장점은 무엇이라고 생각하시나요?";

        // Redis가 이전 질문을 잘 반환하고, Lock(자물쇠) 획득에 성공했다고 가짜 설정
        when(redisSessionService.getLastQuestion(sessionId)).thenReturn(lastQuestion);
        when(redisSessionService.lockQuestionGeneration(sessionId)).thenReturn(true);

        // gRPC 클라이언트가 정상적으로 다음 질문을 만들어왔다고 가짜 설정
        when(aiGrpcClient.generateNextQuestion(sessionId, answer)).thenReturn(nextQuestion);

        // when: 답변 제출 로직 실행
        sessionService.processAnswerAndGenerateQuestion(sessionId, answer);

        // then: 복잡한 5단계 로직이 모두 정확하게 1번씩 호출되었는지 꼼꼼하게 검증
        // 1. 사용자의 답변이 Redis에 JSON으로 저장되었는가?
        verify(redisSessionService, times(1)).addQaToRedis(eq(sessionId), anyString());

        // 2. gRPC로 AI 질문 생성을 요청했는가?
        verify(aiGrpcClient, times(1)).generateNextQuestion(sessionId, answer);

        // 3. AI가 만든 새 질문을 다음을 위해 Redis에 저장했는가?
        verify(redisSessionService, times(1)).setLastQuestion(sessionId, nextQuestion);

        // 4. WebSocket을 통해 프론트엔드로 새 질문을 쏴주었는가?
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/session/" + sessionId + "/question"), any(Map.class)
        );

        // 5. 마지막으로 중복 방지 Lock(자물쇠)을 확실하게 풀었는가?
        verify(redisSessionService, times(1)).unlockQuestionGeneration(sessionId);
    }

}