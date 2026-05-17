package com.interviewmirror.realtime.service;

import static org.mockito.Mockito.verify;
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
  @DisplayName("답변 이벤트 처리 시 답변 기록 후 꼬리 질문 생성을 요청한다.")
  void submitAnswer_RecordsAnswerAndGeneratesFollowUpQuestion() {
    RealtimeAnswerRequest request = new RealtimeAnswerRequest();
    ReflectionTestUtils.setField(request, "sessionId", 1L);
    ReflectionTestUtils.setField(request, "answer", "제 답변입니다.");
    ReflectionTestUtils.setField(request, "emotionResult", "HAPPY");
    ReflectionTestUtils.setField(request, "responseTimeSeconds", 15);

    when(sessionService.recordAnswer(1L, "제 답변입니다.", "HAPPY", 15)).thenReturn("이전 질문입니다.");

    realtimeService.submitAnswer(request);

    verify(sessionService).recordAnswer(1L, "제 답변입니다.", "HAPPY", 15);
    verify(questionGenerationService).generateFollowUpQuestion(1L, "이전 질문입니다.", "제 답변입니다.");
  }
}
