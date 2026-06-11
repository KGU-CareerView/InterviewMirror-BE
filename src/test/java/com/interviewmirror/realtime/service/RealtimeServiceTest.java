package com.interviewmirror.realtime.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.realtime.dto.RealtimeAnswerRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RealtimeServiceTest {

  @Mock private RealtimeStreamManager realtimeStreamManager;

  @Mock private RealtimeFrameBuffer realtimeFrameBuffer;

  @Mock private SessionService sessionService;

  @Mock private RealtimeQuestionGenerationService questionGenerationService;

  @Mock private RealtimeMessagePublisher realtimeMessagePublisher;

  @Mock private SimpMessagingTemplate messagingTemplate;

  @InjectMocks private RealtimeService realtimeService;

  @Test
  @DisplayName("마지막 질문 답변 시 답변 기록 후 꼬리 질문 생성을 요청한다.")
  void submitAnswer_RecordsAnswerAndGeneratesFollowUpQuestion() {
    RealtimeAnswerRequest request = new RealtimeAnswerRequest();
    ReflectionTestUtils.setField(request, "sessionId", 1L);
    ReflectionTestUtils.setField(request, "question", "이전 질문입니다.");
    ReflectionTestUtils.setField(request, "answer", "제 답변입니다.");
    ReflectionTestUtils.setField(request, "emotionResult", "HAPPY");
    ReflectionTestUtils.setField(request, "responseTimeSeconds", 15);
    ReflectionTestUtils.setField(request, "requestNextQuestion", true);

    when(sessionService.recordAnswer(1L, "이전 질문입니다.", "제 답변입니다.", "HAPPY", 15, null))
        .thenReturn("이전 질문입니다.");

    realtimeService.submitAnswer(request);

    verify(sessionService).recordAnswer(1L, "이전 질문입니다.", "제 답변입니다.", "HAPPY", 15, null);
    verify(questionGenerationService).generateFollowUpQuestion(1L, "이전 질문입니다.", "제 답변입니다.");
  }

  @Test
  @DisplayName("초기 질문 목록 답변 시 답변만 기록하고 꼬리 질문 생성은 요청하지 않는다.")
  void submitAnswer_RecordsAnswerWithoutFollowUpQuestion() {
    RealtimeAnswerRequest request = new RealtimeAnswerRequest();
    ReflectionTestUtils.setField(request, "sessionId", 1L);
    ReflectionTestUtils.setField(request, "question", "첫 번째 질문입니다.");
    ReflectionTestUtils.setField(request, "answer", "제 답변입니다.");
    ReflectionTestUtils.setField(request, "emotionResult", "HAPPY");
    ReflectionTestUtils.setField(request, "responseTimeSeconds", 15);

    when(sessionService.recordAnswer(1L, "첫 번째 질문입니다.", "제 답변입니다.", "HAPPY", 15, null))
        .thenReturn("첫 번째 질문입니다.");

    realtimeService.submitAnswer(request);

    verify(sessionService).recordAnswer(1L, "첫 번째 질문입니다.", "제 답변입니다.", "HAPPY", 15, null);
    verifyNoInteractions(questionGenerationService);
  }
}
