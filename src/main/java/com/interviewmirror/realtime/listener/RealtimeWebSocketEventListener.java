package com.interviewmirror.realtime.listener;

import com.interviewmirror.interview.service.SessionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeWebSocketEventListener {

  private final SessionService sessionService;
  private static final List<String> SESSION_TOPIC_PREFIXES =
      List.of("/topic/session/", "/topic/realtime/");

  // [핵심] STOMP의 고유 네트워크 ID와 우리가 사용하는 앱의 SessionID를 연결해주는 장부입니다.
  // 여러 명이 동시에 들어와도 안전하도록 ConcurrentHashMap을 사용합니다.
  private final Map<String, Long> sessionMapping = new ConcurrentHashMap<>();

  /** 프론트엔드가 특정 세션 topic을 구독(Subscribe)할 때 작동합니다. STOMP 네트워크 세션과 앱의 interview sessionId를 연결합니다. */
  @EventListener
  public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    String destination = headerAccessor.getDestination();
    String networkSessionId = headerAccessor.getSessionId();

    extractSessionId(destination)
        .ifPresent(appSessionId -> sessionMapping.put(networkSessionId, appSessionId));
  }

  /** 인터넷이 끊기거나, 브라우저 탭을 닫거나, 에러로 연결이 끊길 때(Disconnect) 무조건 작동합니다. */
  @EventListener
  public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    String networkSessionId = headerAccessor.getSessionId();

    // 1. 끊긴 네트워크 ID로 장부를 뒤져서 몇 번 세션인지 알아냅니다.
    Long appSessionId = sessionMapping.remove(networkSessionId); // 가져옴과 동시에 장부에서 삭제

    // 2. 만약 장부에 기록된 세션이라면, 시스템 강제 일시정지 메서드를 실행합니다.
    if (appSessionId != null) {
      log.warn("[웹소켓 끊김] 연결 종료 감지. 시스템이 App Session [{}]을 일시정지 처리합니다.", appSessionId);
      sessionService.autoPauseSession(appSessionId);
    }
  }

  private Optional<Long> extractSessionId(String destination) {
    if (destination == null) {
      return Optional.empty();
    }

    boolean sessionTopic = SESSION_TOPIC_PREFIXES.stream().anyMatch(destination::startsWith);
    if (!sessionTopic) {
      return Optional.empty();
    }

    try {
      String[] parts = destination.split("/");
      return Optional.of(Long.parseLong(parts[3]));
    } catch (Exception e) {
      log.warn("웹소켓 구독 URL에서 SessionID 추출 실패: {}", destination);
      return Optional.empty();
    }
  }
}
