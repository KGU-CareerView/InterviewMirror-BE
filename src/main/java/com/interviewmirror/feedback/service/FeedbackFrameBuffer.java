package com.interviewmirror.domain.feedback.service;

import com.interviewmirror.domain.feedback.dto.FeedbackResponse;
import com.interviewmirror.domain.feedback.repository.FeedbackBufferRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackFrameBuffer {

  private final FeedbackBufferRepository feedbackBufferRepository;
  private final Map<String, Queue<FeedbackResponse>> buffers = new ConcurrentHashMap<>();

  @Value("${feedback.buffer.max-size-per-session:1000}")
  private int maxSizePerSession;

  public void add(FeedbackResponse response) {
    Queue<FeedbackResponse> queue =
        buffers.computeIfAbsent(response.getSessionId(), key -> new ConcurrentLinkedQueue<>());
    queue.offer(response);

    while (queue.size() > maxSizePerSession) {
      queue.poll();
    }
  }

  @Scheduled(fixedDelayString = "${feedback.buffer.flush-interval-ms:2000}")
  public void flushAll() {
    buffers.keySet().forEach(this::flushSession);
  }

  public void flushSession(String sessionId) {
    Queue<FeedbackResponse> queue = buffers.get(sessionId);
    if (queue == null || queue.isEmpty()) {
      return;
    }

    List<FeedbackResponse> drained = drain(queue);
    if (drained.isEmpty()) {
      return;
    }

    feedbackBufferRepository.appendAll(sessionId, drained);
    if (queue.isEmpty()) {
      buffers.remove(sessionId, queue);
    }
    log.debug("Flushed {} feedback frames for session {}", drained.size(), sessionId);
  }

  private List<FeedbackResponse> drain(Queue<FeedbackResponse> queue) {
    List<FeedbackResponse> responses = new ArrayList<>();
    FeedbackResponse response;
    while ((response = queue.poll()) != null) {
      responses.add(response);
    }
    return responses;
  }
}
