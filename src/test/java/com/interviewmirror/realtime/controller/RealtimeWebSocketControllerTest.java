package com.interviewmirror.realtime.controller;

import static org.mockito.Mockito.verify;

import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.realtime.dto.RealtimeAnswerRequest;
import com.interviewmirror.realtime.service.RealtimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RealtimeWebSocketControllerTest {

  @Mock private RealtimeService realtimeService;

  @Mock private SessionService sessionService;

  @InjectMocks private RealtimeWebSocketController controller;

  @Test
  @DisplayName("WebSocket 답변 제출 시 SessionService에 답변 처리를 위임한다.")
  void submitAnswer_DelegatesToSessionService() {
    RealtimeAnswerRequest request = new RealtimeAnswerRequest();
    ReflectionTestUtils.setField(request, "sessionId", 1L);
    ReflectionTestUtils.setField(request, "answer", "제 답변입니다.");
    ReflectionTestUtils.setField(request, "emotionResult", "HAPPY");
    ReflectionTestUtils.setField(request, "responseTimeSeconds", 15);

    controller.submitAnswer(request);

    verify(sessionService).processAnswerAndGenerateQuestion(1L, "제 답변입니다.", "HAPPY", 15);
  }
}
