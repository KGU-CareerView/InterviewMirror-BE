package com.interviewmirror.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 비동기 스레드 생성 제한
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "aiTaskExecutor")
  public Executor asyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // 평소에 일할 기본 스레드 개수
    executor.setCorePoolSize(10);
    // 최대 접속자가 몰렸을 때 허용할 최대 스레드 개수
    executor.setMaxPoolSize(50);
    // 스레드 50개가 모두 일하고 있을 때, 요청을 대기시킬 줄(Queue)의 길이
    executor.setQueueCapacity(100);
    // 로그에서 오류를 찾기 쉽게 스레드에 이름표를 붙여줍니다.
    executor.setThreadNamePrefix("AI-Worker-");
    executor.initialize();
    return executor;
  }
}
