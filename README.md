# InterviewMirror Backend

Production-ready Spring Boot backend service with layered architecture, domain-driven design, MySQL database integration, and Redis caching.

## 📋 Table of Contents

- [프로젝트 개요](#프로젝트-개요)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [설치 및 실행](#설치-및-실행)
- [개발 환경 설정](#개발-환경-설정)
- [Git 브랜치 전략](#git-브랜치-전략)
- [협업 규칙](#협업-규칙)
- [API 문서](#api-문서)
- [테스트](#테스트)
- [트러블슈팅](#트러블슈팅)

---

## 프로젝트 개요

InterviewMirror는 면접 준비를 돕는 애플리케이션입니다. 이 저장소는 백엔드 서비스를 담당하며, RESTful API를 제공합니다.

**주요 특징:**
- ✅ 도메인 주도 설계 (Domain-Driven Design)
- ✅ 계층화된 아키텍처 (Controller → Service → Repository)
- ✅ Redis 캐싱 지원
- ✅ 전역 예외 처리
- ✅ 통합 테스트 환경 (H2 인메모리 DB)
- ✅ Swagger/OpenAPI 문서화

---

## 기술 스택

| 항목 | 버전 | 용도 |
|------|------|------|
| **Java** | 17 | 프로그래밍 언어 |
| **Spring Boot** | 3.2.4 | 프레임워크 |
| **Spring Data JPA** | - | ORM |
| **Spring Security** | - | 인증/인가 |
| **MySQL** | 8.0+ | 주 데이터베이스 |
| **Redis** | 6.0+ | 캐싱 |
| **Lombok** | - | 보일러플레이트 제거 |
| **Gradle** | 8.6+ | 빌드 도구 |
| **JUnit 5** | - | 테스트 프레임워크 |
| **H2** | - | 테스트용 인메모리 DB |

---

## 프로젝트 구조

```
src/
├── main/
│   ├── java/com/interviewmirror/
│   │   ├── InterviewMirrorApplication.java      # 애플리케이션 진입점
│   │   ├── config/                              # 전역 설정
│   │   │   ├── DatabaseConfig.java
│   │   │   ├── CacheConfig.java
│   │   │   └── SecurityConfig.java
│   │   ├── exception/                           # 전역 예외 처리
│   │   │   ├── BusinessException.java
│   │   │   ├── ErrorResponse.java
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── domain/                              # 도메인 모듈
│   │   │   └── user/                            # User 도메인 (예제)
│   │   │       ├── controller/                  # REST 컨트롤러
│   │   │       ├── service/                     # 비즈니스 로직
│   │   │       ├── repository/                  # 데이터 접근
│   │   │       ├── entity/                      # JPA 엔티티
│   │   │       └── dto/                         # 데이터 전달 객체
│   │   └── util/                                # 유틸리티
│   │       └── ApiResponse.java
│   └── resources/
│       ├── application.yml                      # 기본 설정
│       ├── application-dev.yml                  # 개발 환경 설정
│       ├── application-prod.yml                 # 운영 환경 설정
│       └── schema.sql                           # DB 스키마
│
├── test/
│   ├── java/com/interviewmirror/
│   │   └── domain/user/
│   │       ├── service/UserServiceTest.java
│   │       └── repository/UserRepositoryTest.java
│   └── resources/
│       ├── application-test.yml                 # 테스트 환경 설정
│       └── schema.sql                           # 테스트 DB 스키마
│
build.gradle                                     # Gradle 의존성 관리
settings.gradle                                  # Gradle 설정
gradle/                                          # Gradle Wrapper
gradlew                                          # Gradle Wrapper (Linux/Mac)
gradlew.bat                                      # Gradle Wrapper (Windows)
.env.example                                     # 환경변수 예제
.env                                             # 환경변수 (로컬용, .gitignore에 포함)
```

### 아키텍처 설명

#### 1. **Controller Layer**
- HTTP 요청/응답 처리
- 요청 검증
- 응답 형식 변환

#### 2. **Service Layer**
- 비즈니스 로직 구현
- 트랜잭션 관리
- 캐싱 처리

#### 3. **Repository Layer**
- 데이터베이스 접근
- JPA를 통한 ORM 처리
- 쿼리 정의

#### 4. **Entity & DTO**
- **Entity**: 데이터베이스 테이블 매핑
- **DTO**: 외부 API 통신용 데이터 구조

---

## 설치 및 실행

### 필수 요구사항

- **Java 17 이상**
- **Gradle 8.6 이상** (또는 제공된 Gradle Wrapper 사용)
- **MySQL 8.0 이상**
- **Redis 6.0 이상** (프로덕션 환경)

### 1단계: 저장소 클론

```bash
git clone https://github.com/interviewmirror/backend.git
cd backend
```

### 2단계: 환경변수 설정

```bash
# .env.example을 참고하여 .env 파일 생성
cp .env.example .env

# .env 파일 수정 (실제 DB/Redis 정보)
nano .env
```

**.env 파일 예:**
```env
SERVER_PORT=8080
DB_HOST=localhost
DB_PORT=3306
DB_NAME=interview_mirror
DB_USERNAME=root
DB_PASSWORD=your_password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

### 3단계: 의존성 설치 및 빌드

```bash
# Gradle Wrapper를 사용한 빌드 (권장)
./gradlew build

# 또는 테스트 스킵 (빠른 빌드)
./gradlew build -x test

# Windows 환경
gradlew.bat build
```

### 4단계: 데이터베이스 초기화

```bash
# MySQL에 연결하여 schema.sql 실행
mysql -u root -p < src/main/resources/schema.sql
```

### 5단계: 애플리케이션 실행

```bash
# Gradle을 사용하여 직접 실행
./gradlew bootRun --args='--spring.profiles.active=dev'

# 또는 JAR 빌드 후 실행
./gradlew build
java -jar build/libs/backend-1.0.0.jar --spring.profiles.active=dev

# Windows 환경
gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

**애플리케이션 시작 확인:**
- API 서버: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## Docker Compose로 전체 스택 실행

### 빠른 시작 (권장)

```bash
# 1. 전체 스택 시작 (MySQL, Redis, Backend)
docker-compose up -d

# 2. 로그 확인
docker-compose logs -f backend

# 3. API 테스트
curl http://localhost:8080/swagger-ui.html

# 4. 멈추기
docker-compose down
```

### 개별 서비스 관리

```bash
# 특정 서비스만 시작
docker-compose up -d mysql redis

# 또는
docker-compose up -d backend

# 서비스 상태 확인
docker-compose ps

# 서비스 로그 보기
docker-compose logs -f mysql
docker-compose logs -f redis
docker-compose logs -f backend

# 데이터 유지하며 서비스 중지
docker-compose stop

# 데이터 포함하여 전체 삭제
docker-compose down -v
```

### Docker 환경 변수

docker-compose.yml에서 설정된 환경 변수:

| 환경변수 | 기본값 | 설명 |
|---------|--------|------|
| DB_HOST | mysql | MySQL 호스트명 |
| DB_PORT | 3306 | MySQL 포트 |
| DB_NAME | interview_db | 데이터베이스 이름 |
| DB_USERNAME | interview_user | DB 사용자명 |
| DB_PASSWORD | interview_password | DB 암호 |
| REDIS_HOST | redis | Redis 호스트명 |
| REDIS_PORT | 6379 | Redis 포트 |

### 데이터 관리

```bash
# MySQL 데이터 확인
docker-compose exec mysql mysql -uinterview_user -pinterview_password interview_db

# Redis 데이터 확인
docker-compose exec redis redis-cli

# 데이터베이스 초기화 (전체 삭제)
docker-compose down -v
docker-compose up -d
```

### 문제 해결

```bash
# MySQL 접속 오류
docker-compose logs mysql

# Redis 접속 오류
docker-compose logs redis

# 백엔드 시작 실패
docker-compose logs backend

# 포트 충돌
# 포트 변경 후 재시작
# docker-compose.yml에서 ports 수정
```

---

## 개발 환경 설정

### IDE 설정 (IntelliJ IDEA)

1. **Lombok 플러그인 설치**
   - `Preferences/Settings → Plugins → Lombok` 검색 및 설치

2. **Annotation Processing 활성화**
   - `Preferences/Settings → Build, Execution, Deployment → Compiler → Annotation Processors`
   - ✅ Enable annotation processing 체크

3. **Gradle 설정**
   - `Preferences/Settings → Build, Execution, Deployment → Build Tools → Gradle`
   - Gradle JVM: JDK 17 이상 선택
   - Gradle distribution: Gradle Wrapper 선택 (Use Gradle from 'gradle-wrapper.properties' file)

### IDE 설정 (VS Code)

필요한 확장:
- **Extension Pack for Java** (Microsoft)
- **Spring Boot Extension Pack** (Microsoft)
- **Maven for Java** (Microsoft)

### 로컬 개발 서버 실행

```bash
# Gradle Wrapper로 개발 프로필 실행 (실시간 리로드 지원)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 또는 IDE에서 직접 실행
# InterviewMirrorApplication.java 우클릭 → Run
```

---

## Git 브랜치 전략

이 프로젝트는 **Git Flow** 브랜치 모델을 따릅니다.

### 브랜치 종류

| 브랜치 | 목적 | 기반 | 병합 대상 |
|--------|------|------|---------|
| `main` | 프로덕션 배포 | - | - |
| `develop` | 개발 통합 | `main` | `main` |
| `feature/*` | 기능 개발 | `develop` | `develop` |
| `bugfix/*` | 버그 수정 | `develop` | `develop` |
| `hotfix/*` | 긴급 수정 | `main` | `main`, `develop` |
| `release/*` | 배포 준비 | `develop` | `main`, `develop` |

### 브랜치 이름 규칙

```
feature/[ticket-id]-[description]
bugfix/[ticket-id]-[description]
hotfix/[ticket-id]-[description]
release/[version]
```

**예시:**
```
feature/IM-123-user-authentication
bugfix/IM-456-cache-invalidation
hotfix/IM-789-critical-security-issue
release/1.2.0
```

### 브랜치 생성 및 작업 흐름

#### 1. 기능 개발 시작

```bash
# develop 브랜치 최신화
git checkout develop
git pull origin develop

# 기능 브랜치 생성
git checkout -b feature/IM-123-user-auth

# 작업 진행...
git add .
git commit -m "feat: implement user authentication"
git push -u origin feature/IM-123-user-auth
```

#### 2. Pull Request 생성

- GitHub에서 PR 생성
- Base: `develop`, Compare: `feature/IM-123-user-auth`
- 템플릿에 따라 PR 설명 작성
- 코드 리뷰 요청 (최소 1명)

#### 3. 코드 리뷰 및 병합

```bash
# 리뷰 완료 후 병합
# - PR 스쿼시 병합 또는 일반 병합 (팀 규칙에 따름)
# - 작업 브랜치 삭제
```

#### 4. 배포 준비

```bash
# release 브랜치 생성
git checkout -b release/1.0.0 develop
# 버전 업데이트, 최종 테스트 등...
git commit -m "chore: bump version to 1.0.0"

# main에 병합
git checkout main
git merge --no-ff release/1.0.0
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin main

# develop에도 병합
git checkout develop
git merge --no-ff release/1.0.0
git push origin develop

# release 브랜치 삭제
git branch -d release/1.0.0
git push origin --delete release/1.0.0
```

---

## 협업 규칙

### 1. 커밋 메시지 규칙

**형식:**
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type:**
- `feat`: 새 기능
- `fix`: 버그 수정
- `docs`: 문서 수정
- `style`: 코드 스타일 변경 (공백, 세미콜론 등)
- `refactor`: 기능 변경 없는 코드 리팩토링
- `perf`: 성능 개선
- `test`: 테스트 추가/수정
- `chore`: 빌드, 패키지 매니저 등 변경

**예시:**
```
feat(user): add email verification functionality

- Implement email verification service
- Add email configuration to application.yml
- Create verification token entity

Closes #123
```

### 2. 코드 스타일

#### Java 코딩 컨벤션

```java
// 1. 클래스/메서드 명명
public class UserController { }           // PascalCase
public void getUserById() { }             // camelCase

// 2. 상수 명명
public static final String API_VERSION = "1.0"; // UPPER_SNAKE_CASE

// 3. 메서드 길이
// - 한 메서드는 한 가지 책임만 (SRP)
// - 최대 30줄 권장

// 4. Lombok 활극
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String email;
}

// 5. 로깅
private static final Logger logger = LoggerFactory.getLogger(UserService.class);

// 6. 주석
// Bad: 뻔한 주석
// userId를 조회한다
// Good: 의도와 복잡도를 설명하는 주석
// 성능상 캐시를 확인한 후 DB 조회
// (캐시 미스 시 동시성 문제 방지를 위해 lock 사용)
```

#### 패키지 구조 규칙

```
domain/[domain-name]/
├── controller/    # REST 엔드포인트
├── service/       # 비즈니스 로직 (interface + impl)
├── repository/    # 데이터 접근
├── entity/        # JPA 엔티티
└── dto/          # 요청/응답 DTO
```

### 3. Pull Request 규칙

#### PR 제목
- 커밋 메시지 규칙과 동일
- 영문 작성
- 간결하고 명확하게

#### PR 설명 템플릿
```markdown
## 변경 사항
- 기능/버그 설명

## 테스트 방법
- [ ] 테스트 케이스 1
- [ ] 테스트 케이스 2

## 스크린샷 (필요 시)
<!-- 스크린샷 추가 -->

## 체크리스트
- [ ] 코드 리뷰 완료
- [ ] 테스트 통과
- [ ] 문서 업데이트
```

#### PR 리뷰 규칙

**리뷰어 책임:**
- ✅ 기능 요구사항 충족 확인
- ✅ 코드 스타일 확인
- ✅ 성능 문제 확인
- ✅ 보안 취약점 확인
- ✅ 테스트 커버리지 확인

**승인 기준:**
- 최소 1명의 리뷰 승인
- 모든 conversation 해결
- CI/CD 파이프라인 통과

### 4. 코드 리뷰 체크리스트

```
코드 품질:
- [ ] 함수/메서드는 SRP를 따르는가?
- [ ] 네이밍이 명확한가?
- [ ] 중복 코드가 있는가?
- [ ] 예외 처리가 적절한가?

테스트:
- [ ] 새 기능에 테스트가 있는가?
- [ ] 테스트는 의미 있는가?
- [ ] Edge case를 커버하는가?

성능:
- [ ] N+1 쿼리 문제는 없는가?
- [ ] 불필요한 캐시 무효화는 없는가?
- [ ] DB 인덱스는 활용되는가?

보안:
- [ ] SQL Injection 위험은 없는가?
- [ ] 인증/인가 확인은 적절한가?
- [ ] 민감한 정보 로깅은 없는가?

문서:
- [ ] 복잡한 로직에 주석이 있는가?
- [ ] API 문서는 최신인가?
```

### 5. 이슈 관리

#### 이슈 타입
- `Bug`: 버그 보고
- `Feature`: 새 기능 요청
- `Enhancement`: 기존 기능 개선
- `Documentation`: 문서 관련
- `Task`: 일반 작업

#### 이슈 제목 규칙
```
[TYPE] 간단한 설명

예:
[Bug] User cache not invalidating on update
[Feature] Add email verification functionality
[Enhancement] Optimize database queries
```

### 6. 릴리즈 및 배포

#### 버전 관리 (Semantic Versioning)
```
MAJOR.MINOR.PATCH
1.2.3
├── MAJOR: 호환되지 않는 API 변경
├── MINOR: 하위 호환 기능 추가
└── PATCH: 하위 호환 버그 수정
```

#### 배포 체크리스트
```
- [ ] develop 모든 변경사항 merge 완료
- [ ] 모든 테스트 통과
- [ ] 성능 테스트 완료
- [ ] 보안 검수 완료
- [ ] 데이터베이스 마이그레이션 검증
- [ ] 배포 롤백 계획 수립
- [ ] 모니터링 대시보드 준비
```

---

## API 문서

### Swagger UI 접근
- **URL**: `http://localhost:8080/swagger-ui.html`
- **API Docs JSON**: `http://localhost:8080/v3/api-docs`

### 주요 엔드포인트

#### User 도메인

| 메서드 | 엔드포인트 | 설명 |
|--------|----------|------|
| `POST` | `/api/users` | 사용자 생성 |
| `GET` | `/api/users/{id}` | 사용자 조회 |
| `PUT` | `/api/users/{id}` | 사용자 수정 |
| `DELETE` | `/api/users/{id}` | 사용자 삭제 |

### API 응답 형식

**성공 응답:**
```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

**에러 응답:**
```json
{
  "success": false,
  "message": "Invalid email format",
  "errorCode": "INVALID_REQUEST"
}
```

---

## 테스트

### 테스트 구조

```
src/test/
├── java/com/interviewmirror/
│   └── domain/user/
│       ├── service/UserServiceTest.java      # 서비스 유닛 테스트
│       └── repository/UserRepositoryTest.java # 리포지토리 통합 테스트
└── resources/
    └── application-test.yml                   # 테스트 설정
```

### 테스트 실행

```bash
# Gradle Wrapper를 사용한 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests UserServiceTest

# 특정 테스트 메서드만 실행
./gradlew test --tests UserServiceTest.testCreateUser

# 테스트 실행 및 리포트 생성
./gradlew test --info

# Windows 환경
gradlew.bat test
```

### 테스트 작성 가이드

```java
@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    @DisplayName("사용자 생성 성공")
    void testCreateUserSuccess() {
        // Given
        User user = new User();
        user.setEmail("test@example.com");
        user.setName("Test User");
        
        // When
        when(userRepository.save(any(User.class))).thenReturn(user);
        User result = userService.createUser(user);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(userRepository).save(any(User.class));
    }
}
```

---

## 트러블슈팅

### 자주 발생하는 문제

#### 1. Gradle 의존성 캐시 문제
```bash
# Gradle 캐시 제거 및 재빌드
./gradlew clean build --refresh-dependencies

# 또는 전체 Gradle 캐시 삭제
rm -rf ~/.gradle/caches
./gradlew build
```

#### 2. Lombok이 작동하지 않음
```
- IDE 재시작
- Annotation Processing 활성화 확인
- Lombok 플러그인 재설치
```

#### 3. 데이터베이스 연결 실패
```bash
# MySQL 상태 확인
mysql -u root -p -e "SELECT 1;"

# 또는 포트 확인
netstat -an | grep 3306
```

#### 4. Redis 연결 오류
```bash
# Redis 상태 확인
redis-cli ping

# Redis 시작
redis-server
```

#### 5. 포트 이미 사용 중
```bash
# 8080 포트 사용 중인 프로세스 확인
lsof -i :8080

# 프로세스 종료
kill -9 <PID>
```

#### 6. 테스트 실패
```bash
# 테스트 상세 로그 보기
./gradlew test --info --stacktrace

# 특정 테스트 디버그
./gradlew test --tests UserServiceTest --debug

# 테스트 결과 리포트 확인
# build/reports/tests/test/index.html 참고
```

### 문제 보고

문제가 발생했을 때:
1. 에러 메시지와 스택 트레이스 수집
2. GitHub Issues에서 기존 이슈 검색
3. 새 Issue 생성 (있으면 댓글로 추가 정보 제공)
4. 개발 팀에 Slack/Email로 알림

---

## 추가 리소스

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Spring Security](https://spring.io/projects/spring-security)
- [Maven 공식 사이트](https://maven.apache.org/)

---

## 라이선스

[프로젝트 라이선스 정보]

## 기여 방법

이 프로젝트에 기여하고 싶다면, 위의 Git 브랜치 전략과 협업 규칙을 따라주세요.

---

**마지막 업데이트:** 2026-04-13  
**유지보수자:** InterviewMirror Team
