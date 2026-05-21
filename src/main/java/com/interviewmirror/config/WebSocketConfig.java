package com.interviewmirror.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
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

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws-interview").setAllowedOriginPatterns("*").withSockJS();
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
}
