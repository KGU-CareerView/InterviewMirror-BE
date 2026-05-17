package com.interviewmirror.realtime.service;

import com.interviewmirror.realtime.dto.RealtimeResponse;
import com.interviewmirror.realtime.repository.RealtimeBufferRepository;
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
public class RealtimeFrameBuffer {

  private final RealtimeBufferRepository realtimeBufferRepository;
  private final Map<String, Queue<RealtimeResponse>> buffers = new ConcurrentHashMap<>();

  @Value("${realtime.buffer.max-size-per-session:1000}")
  private int maxSizePerSession;

  public void add(RealtimeResponse response) {
    Queue<RealtimeResponse> queue =
        buffers.computeIfAbsent(response.getSessionId(), key -> new ConcurrentLinkedQueue<>());
    queue.offer(response);

    while (queue.size() > maxSizePerSession) {
      queue.poll();
    }
  }

  @Scheduled(fixedDelayString = "${realtime.buffer.flush-interval-ms:2000}")
  public void flushAll() {
    buffers.keySet().forEach(this::flushSession);
  }

  public void flushSession(String sessionId) {
    Queue<RealtimeResponse> queue = buffers.get(sessionId);
    if (queue == null || queue.isEmpty()) {
      return;
    }

    List<RealtimeResponse> drained = drain(queue);
    if (drained.isEmpty()) {
      return;
    }

    realtimeBufferRepository.appendAll(sessionId, drained);
    if (queue.isEmpty()) {
      buffers.remove(sessionId, queue);
    }
    log.debug("Flushed {} realtime frames for session {}", drained.size(), sessionId);
  }

  private List<RealtimeResponse> drain(Queue<RealtimeResponse> queue) {
    List<RealtimeResponse> responses = new ArrayList<>();
    RealtimeResponse response;
    while ((response = queue.poll()) != null) {
      responses.add(response);
    }
    return responses;
  }
}
