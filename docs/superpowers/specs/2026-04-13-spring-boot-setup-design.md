# Spring Boot Layered Architecture Setup - InterviewMirror Backend

**Date:** 2026-04-13  
**Project:** InterviewMirror Backend  
**Status:** Design

---

## 1. Overview

Spring Boot 3.x 기반 레이어드 아키텍처 설계로 MySQL 데이터베이스와 Redis 캐싱을 활용한 기본 프로젝트 스캐폴드를 구축합니다.

---

## 2. Project Structure

```
src/main/java/com/interviewmirror/
├── domain/                          # 도메인별 모듈
│   ├── user/
│   │   ├── controller/
│   │   │   └── UserController.java
│   │   ├── service/
│   │   │   ├── UserService.java
│   │   │   └── UserServiceImpl.java
│   │   ├── repository/
│   │   │   └── UserRepository.java
│   │   ├── entity/
│   │   │   └── User.java
│   │   └── dto/
│   │       ├── request/
│   │       └── response/
│   ├── interview/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   └── (기타 도메인)
├── config/                          # 스프링 설정 클래스
│   ├── DatabaseConfig.java
│   ├── CacheConfig.java            # Redis 캐싱 설정
│   └── AppConfig.java
├── exception/                       # 커스텀 예외 및 핸들러
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
├── cache/                          # Redis 캐싱 전략
│   └── CacheKeyGenerator.java
├── util/                           # 공통 유틸리티
│   └── (공통 유틸리티)
└── InterviewMirrorApplication.java

src/main/resources/
├── application.yml                 # 기본 설정 (환경변수 참조)
├── application-dev.yml            # 개발 환경
├── application-prod.yml           # 운영 환경
└── schema.sql                      # DB 스키마 초기화

프로젝트 루트/
├── .env                            # 민감 정보 (로컬 개발용, 버전관리 제외)
├── .env.example                    # .env 템플릿 (버전관리 포함)
└── .gitignore                      # .env 제외

src/test/java/com/interviewmirror/
├── domain/
│   └── (도메인별 테스트)
├── integration/                    # 통합 테스트
└── unit/                          # 단위 테스트
```

---

## 3. Technology Stack

| 항목 | 기술 |
|------|------|
| Framework | Spring Boot 3.x |
| ORM | Spring Data JPA |
| Database | MySQL 8.0+ |
| Caching | Spring Cache + Redis |
| Build Tool | Maven / Gradle |
| Database Driver | MySQL Connector/J |
| Utility | Lombok |
| API Docs | SpringDoc OpenAPI (선택) |
| Testing | JUnit 5, Mockito |

---

## 4. Layer Architecture

### 4.1 Controller Layer
- REST API 엔드포인트 정의
- 요청 유효성 검증 (@Valid)
- 응답 DTO로 데이터 반환
- HTTP 상태 코드 관리

### 4.2 Service Layer
- 비즈니스 로직 구현
- 트랜잭션 관리 (@Transactional)
- 캐싱 처리 (@Cacheable, @CacheEvict)
- 도메인 간 조율

### 4.3 Repository Layer
- Spring Data JPA 사용
- 데이터 접근 추상화
- 커스텀 쿼리 (@Query)
- 배치 처리

### 4.4 Entity Layer
- JPA 도메인 모델
- DB 테이블 매핑
- 관계 설정 (@OneToMany, @ManyToOne 등)

---

## 5. Redis Caching Strategy

### 5.1 캐싱 적용 원칙
- **읽기 작업**: `@Cacheable` - 조회 결과 캐싱
- **쓰기 작업**: `@CacheEvict` - 관련 캐시 제거
- **갱신 작업**: `@CachePut` - 캐시 업데이트

### 5.2 캐시 키 네이밍 규칙
```
entity:id                    # 단일 엔티티
entity:list                  # 리스트 조회
entity:search:keyword        # 검색 결과
```

### 5.3 TTL 설정
- 기본 TTL: 30분 (1800초)
- 설정 가능하게 `application.yml`에서 관리

### 5.4 캐시 비활성화 프로필
- `dev` 프로필에서 개발 편의성을 위해 캐시 비활성화 가능

---

## 6. Configuration

### 6.1 application.yml
```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:interview_mirror}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
  cache:
    type: redis
    redis:
      time-to-live: 1800000  # 30분
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
```

### 6.2 .env (개발 환경, .gitignore에 등재)
```env
SERVER_PORT=8080
DB_HOST=localhost
DB_PORT=3306
DB_NAME=interview_mirror
DB_USERNAME=root
DB_PASSWORD=your_password_here
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

### 6.3 .env.example (버전관리, 팀 가이드용)
```env
SERVER_PORT=8080
DB_HOST=localhost
DB_PORT=3306
DB_NAME=interview_mirror
DB_USERNAME=root
DB_PASSWORD=
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

### 6.2 application-dev.yml
- 캐싱 비활성화
- SQL 로깅 활성화
- DDL 자동 생성 (create-drop)

---

## 7. Exception Handling

### 7.1 GlobalExceptionHandler
- `@RestControllerAdvice`로 전역 예외 처리
- 커스텀 예외 (`BusinessException`) 정의
- 일관된 에러 응답 포맷

### 7.2 에러 응답 구조
```json
{
  "status": 400,
  "message": "에러 메시지",
  "timestamp": "2026-04-13T10:30:00Z"
}
```

---

## 8. Testing Strategy

### 8.1 단위 테스트
- Service 계층 테스트 (Mockito)
- Repository 테스트 (H2 인메모리 DB)

### 8.2 통합 테스트
- `@SpringBootTest` 사용
- 실제 MySQL + Redis 환경 테스트 (선택적)

---

## 9. Dependencies (pom.xml / build.gradle)

**Spring Boot Starter:**
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-data-redis
- spring-boot-starter-validation

**Drivers & Libraries:**
- mysql-connector-java
- lombok
- springdoc-openapi (선택)

**Testing:**
- spring-boot-starter-test
- h2 (인메모리 DB)

---

## 10. Implementation Phases

### Phase 1: 기본 구조 설정
- Maven/Gradle 프로젝트 생성
- 의존성 추가
- 패키지 구조 생성

### Phase 2: 설정 파일
- MySQL 연결 설정
- Redis 연결 설정
- JPA 설정

### Phase 3: 기본 예제 엔티티 & 레포지토리
- Sample 엔티티 (예: User)
- Sample Repository
- Sample Service & Controller

### Phase 4: 캐싱 설정
- Redis 캐시 설정 클래스
- 예제 캐싱 적용

### Phase 5: 예외 처리
- GlobalExceptionHandler
- 커스텀 예외 클래스

### Phase 6: 테스트 설정
- 테스트 환경 설정
- 기본 테스트 케이스

---

## 11. Success Criteria

- ✅ 프로젝트 구조가 명확하고 확장 가능해야 함
- ✅ MySQL과 Redis가 정상 연동됨
- ✅ 기본 CRUD 작업이 캐싱과 함께 작동함
- ✅ 예외 처리가 일관되게 작동함
- ✅ 개발 환경에서 즉시 개발 시작 가능해야 함

---

## 12. Notes

- JDK 17+ 권장 (Spring Boot 3.x)
- MySQL 8.0 이상 권장
- Redis 로컬 또는 Docker로 실행 가능
