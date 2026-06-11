package com.interviewmirror.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.interviewmirror.infrastructure.S3Service;
import com.interviewmirror.interview.dto.SessionCreateResponse;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  @InjectMocks private SessionService sessionService;

  @Mock private InterviewResultRepository resultRepository;

  @Mock private InterviewDetailRepository detailRepository;

  @Mock private RedisSessionService redisSessionService;

  @Mock private AudioScoreService audioScoreService;

  @Mock private S3Service s3Service;

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
  @DisplayName("답변 기록 시 DB에 InterviewDetail을 즉시 저장하고 이전 질문을 반환한다.")
  void recordAnswerTest() {
    Long sessionId = 1L;
    String answer = "이것은 답변입니다.";
    InterviewResult mockResult =
        InterviewResult.builder()
            .sessionId(sessionId)
            .userId(1L)
            .sessionState(InterviewSessionState.IN_PROGRESS.name())
            .build();

    given(redisSessionService.getLastQuestion(sessionId)).willReturn("이전 질문입니다.");
    given(resultRepository.findById(sessionId)).willReturn(Optional.of(mockResult));

    String previousQuestion = sessionService.recordAnswer(sessionId, null, answer, "HAPPY", 15, null);

    assertThat(previousQuestion).isEqualTo("이전 질문입니다.");
    verify(detailRepository).save(any(InterviewDetail.class));
  }

  @Test
  @DisplayName("답변 기록 시 프론트엔드가 보낸 질문 텍스트를 우선 사용한다.")
  void recordAnswerTest_PrefersProvidedQuestion() {
    Long sessionId = 1L;
    String answer = "이것은 답변입니다.";
    String providedQuestion = "두 번째 질문입니다.";
    InterviewResult mockResult =
        InterviewResult.builder()
            .sessionId(sessionId)
            .userId(1L)
            .sessionState(InterviewSessionState.IN_PROGRESS.name())
            .build();

    given(resultRepository.findById(sessionId)).willReturn(Optional.of(mockResult));

    String previousQuestion =
        sessionService.recordAnswer(sessionId, providedQuestion, answer, "HAPPY", 15, null);

    assertThat(previousQuestion).isEqualTo(providedQuestion);
    verify(detailRepository).save(any(InterviewDetail.class));
    verify(redisSessionService, never()).getLastQuestion(sessionId);
  }
}
