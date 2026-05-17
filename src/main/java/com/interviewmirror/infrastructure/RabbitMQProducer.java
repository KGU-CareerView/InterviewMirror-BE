package com.interviewmirror.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RabbitMQProducer {

  @Value("${rabbitmq.exchange.interview:interview.exchange}")
  private String exchange;

  @Value("${rabbitmq.routing.report:report.routing.key}")
  private String routingKey;

  public void sendReportRequest(Long sessionId) {
    log.warn("=========================================================");
    log.warn("[MOCK RabbitMQ] 리포트 생성 메시지 발행 시뮬레이션 (세션 ID: {})", sessionId);
    log.warn("[MOCK RabbitMQ] Exchange: {}, RoutingKey: {}", exchange, routingKey);
    log.warn("=========================================================");
  }
}
