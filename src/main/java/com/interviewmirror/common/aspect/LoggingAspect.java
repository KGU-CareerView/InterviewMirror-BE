package com.interviewmirror.common.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

  private static final int MAX_QUERY_LENGTH = 150;
  private static final long SLOW_REQUEST_THRESHOLD_MS = 2000L;

  @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
  public void restControllerMethods() {}

  @Around("restControllerMethods()")
  public Object logRequestAndResponse(ProceedingJoinPoint joinPoint) throws Throwable {
    HttpServletRequest request = getCurrentRequest();
    if (request == null || shouldSkip(request.getRequestURI())) {
      return joinPoint.proceed();
    }

    long startTime = System.nanoTime();
    String method = request.getMethod();
    String requestUri = request.getRequestURI();
    String queryString = formatQueryString(request.getQueryString());

    log.debug("[REQUEST] {} {}{}", method, requestUri, queryString);

    try {
      Object result = joinPoint.proceed();
      long duration = getDurationMillis(startTime);

      log.debug("[RESPONSE] {} {} | Time: {}ms", method, requestUri, duration);
      logSlowRequestIfNeeded(requestUri, duration);

      return result;
    } catch (Throwable e) {
      long duration = getDurationMillis(startTime);

      log.warn(
          "[EXCEPTION] {} {} | Time: {}ms | Error: {}",
          method,
          requestUri,
          duration,
          e.getMessage());
      logSlowRequestIfNeeded(requestUri, duration);

      throw e;
    }
  }

  private HttpServletRequest getCurrentRequest() {
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      return attributes.getRequest();
    }
    return null;
  }

  private boolean shouldSkip(String requestUri) {
    return requestUri.startsWith("/actuator")
        || requestUri.startsWith("/api/actuator")
        || requestUri.startsWith("/swagger-ui")
        || requestUri.startsWith("/api/swagger-ui")
        || requestUri.startsWith("/api-docs")
        || requestUri.startsWith("/api/api-docs")
        || requestUri.equals("/favicon.ico");
  }

  private String formatQueryString(String queryString) {
    if (queryString == null || queryString.isBlank()) {
      return "";
    }

    String maskedQueryString = maskSensitiveData(queryString);
    if (maskedQueryString.length() > MAX_QUERY_LENGTH) {
      maskedQueryString = maskedQueryString.substring(0, MAX_QUERY_LENGTH) + "...(omitted)";
    }

    return "?" + maskedQueryString;
  }

  private String maskSensitiveData(String value) {
    return value.replaceAll(
        "(?i)(password|token|accessToken|refreshToken|authorization)=([^&\\s]+)", "$1=***");
  }

  private long getDurationMillis(long startTime) {
    return (System.nanoTime() - startTime) / 1_000_000;
  }

  private void logSlowRequestIfNeeded(String requestUri, long duration) {
    if (duration > SLOW_REQUEST_THRESHOLD_MS) {
      log.warn("[SLOW REQUEST] {} took {}ms", requestUri, duration);
    }
  }
}
