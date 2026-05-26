package com.interviewmirror.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  private static final int MESSAGE_SIZE_LIMIT_BYTES = 8 * 1024 * 1024;

  @Bean
  public ServletServerContainerFactoryBean webSocketContainer() {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(MESSAGE_SIZE_LIMIT_BYTES);
    container.setMaxBinaryMessageBufferSize(MESSAGE_SIZE_LIMIT_BYTES);
    return container;
  }

  @Value("${cors.allowed-origins:}")
  private String allowedOrigins;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws-interview")
        .setAllowedOriginPatterns(getAllowedOrigins())
        .withSockJS();
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic"); // 프론트 구독 경로
    registry.setApplicationDestinationPrefixes("/app"); // 서버 메시지 수신 경로
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    registry.setMessageSizeLimit(MESSAGE_SIZE_LIMIT_BYTES);
    registry.setSendBufferSizeLimit(MESSAGE_SIZE_LIMIT_BYTES);
  }

  private String[] getAllowedOrigins() {
    List<String> origins = new ArrayList<>();
    if (!allowedOrigins.isBlank()) {
      origins.addAll(
          Arrays.stream(allowedOrigins.split(","))
              .map(String::trim)
              .filter(origin -> !origin.isBlank())
              .toList());
    }
    origins.add("http://localhost:5173");
    origins.add("http://localhost:5174");
    origins.add("https://interview-mirror-fe.vercel.app");
    return origins.toArray(String[]::new);
  }
}
