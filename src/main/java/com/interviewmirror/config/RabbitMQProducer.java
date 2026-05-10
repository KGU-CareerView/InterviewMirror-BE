package com.interviewmirror.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQProducer {
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.interview}")
    private String exchange;

    @Value("${rabbitmq.routing.report}")
    private String routingKey;

    public void sendReportRequest(Long sessionId) {
        log.info("[RabbitMQ] 세션 ID {} 최종 리포트 생성 메시지 발행", sessionId);

        // 실무에서는 MessageConverter를 통해 DTO를 JSON으로 변환하여 전송하는 것을 권장합니다.
        // ReportRequestDto dto = new ReportRequestDto(sessionId, "REQUEST_REPORT");
        // rabbitTemplate.convertAndSend(exchange, routingKey, dto);

        rabbitTemplate.convertAndSend(exchange, routingKey, sessionId);
    }


    /*private final RabbitTemplate rabbitTemplate;

    public void sendReportRequest(Long sessionId) {
        // 면접 종료 후 최종 리포트 생성을 큐에 삽입
        rabbitTemplate.convertAndSend("interview.exchange", "report.routing.key", sessionId);
    }*/
}