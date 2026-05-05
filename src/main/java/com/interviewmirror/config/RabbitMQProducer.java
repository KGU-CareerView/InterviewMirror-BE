package com.interviewmirror.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RabbitMQProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendReportRequest(Long sessionId) {
        // 면접 종료 후 최종 리포트 생성을 큐에 삽입
        rabbitTemplate.convertAndSend("interview.exchange", "report.routing.key", sessionId);
    }
}