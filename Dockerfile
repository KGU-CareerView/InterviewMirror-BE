# 빌드 스테이지
FROM gradle:8.6-jdk17 AS builder

WORKDIR /build

# 프로젝트 복사
COPY build.gradle settings.gradle gradle/ ./
COPY src ./src

# 빌드 (테스트 스킵)
RUN gradle build -x test

# 런타임 스테이지
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# 빌드 스테이지에서 JAR 복사
COPY --from=builder /build/build/libs/backend-1.0.0.jar app.jar

# 환경 변수 설정
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SPRING_PROFILES_ACTIVE=dev

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD java -jar app.jar --actuator.health 2>/dev/null || exit 1

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
