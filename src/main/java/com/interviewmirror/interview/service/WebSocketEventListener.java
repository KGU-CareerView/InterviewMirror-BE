package com.interviewmirror.interview.component; // 패키지 경로는 프로젝트에 맞게 수정하세요

import com.interviewmirror.interview.service.SessionService;
import java.util.Map;
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
public class WebSocketEventListener {

  private final SessionService sessionService;

  // [핵심] STOMP의 고유 네트워크 ID와 우리가 사용하는 앱의 SessionID를 연결해주는 장부입니다.
  // 여러 명이 동시에 들어와도 안전하도록 ConcurrentHashMap을 사용합니다.
  private final Map<String, Long> sessionMapping = new ConcurrentHashMap<>();

  /**
   * 프론트엔드가 특정 방(예: /topic/session/5/...)을 구독(Subscribe)할 때 작동합니다. 이때 "아하, 이 네트워크 접속자는 5번 세션 유저구나!"
   * 하고 장부에 적어둡니다.
   */
  @EventListener
  public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
    StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
    String destination = headerAccessor.getDestination(); // 예: /topic/session/5/emotion
    String networkSessionId = headerAccessor.getSessionId();

    // 구독하는 URL에 /topic/session/ 이 포함되어 있다면
    if (destination != null && destination.startsWith("/topic/session/")) {
      try {
        // URL을 쪼개서 세션 번호(5)를 찾아냅니다.
        String[] parts = destination.split("/");
        Long appSessionId = Long.parseLong(parts[3]);

        // 장부에 기록 (네트워크 ID -> 앱 세션 ID)
        sessionMapping.put(networkSessionId, appSessionId);
      } catch (Exception e) {
        log.warn("웹소켓 구독 URL에서 SessionID 추출 실패: {}", destination);
      }
    }
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
}
