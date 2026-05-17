package com.interviewmirror.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.infrastructure.S3Service;
import com.interviewmirror.interview.dto.SessionCreateResponse;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  @InjectMocks private SessionService sessionService;

  @Mock private InterviewResultRepository resultRepository;

  @Mock private RedisSessionService redisSessionService;

  @Mock private S3Service s3Service;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("면접 세션 생성 테스트 - DB 저장 후 응답 DTO 반환 및 Redis 상태 업데이트")
  void createSessionTest() {
    Long userId = 1L;
    InterviewResult mockResult =
        InterviewResult.builder()
            .sessionId(100L)
            .userId(userId)
            .sessionState(InterviewSessionState.READY.name())
            .createTime(LocalDateTime.now())
            .build();

    given(resultRepository.save(any(InterviewResult.class))).willReturn(mockResult);

    SessionCreateResponse response = sessionService.createSession(userId);

    verify(redisSessionService).updateSessionState(100L, InterviewSessionState.READY.name());
    assertThat(response.getSessionId()).isEqualTo(100L);
    assertThat(response.getSessionState()).isEqualTo(InterviewSessionState.READY.name());
  }

  @Test
  @DisplayName("답변 기록 시 마지막 질문과 답변을 Redis Q&A에 저장하고 이전 질문을 반환한다.")
  void recordAnswerTest() {
    Long sessionId = 1L;
    String answer = "이것은 답변입니다.";

    given(redisSessionService.getLastQuestion(sessionId)).willReturn("이전 질문입니다.");

    String previousQuestion = sessionService.recordAnswer(sessionId, answer, "HAPPY", 15);

    assertThat(previousQuestion).isEqualTo("이전 질문입니다.");
    verify(redisSessionService).addQaToRedis(eq(sessionId), anyString());
  }
}
