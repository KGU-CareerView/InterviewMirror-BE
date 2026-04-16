FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# 로컬에서 빌드된 JAR 복사
COPY build/libs/backend-1.0.0.jar app.jar

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD java -jar app.jar --actuator.health 2>/dev/null || exit 1

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
