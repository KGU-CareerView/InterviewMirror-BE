# Spring Boot Project Initialization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize a production-ready Spring Boot project with layered architecture, MySQL database integration, Redis caching, and example domain implementation.

**Architecture:** Domain-driven structure with separate config, exception, cache, util folders. Each domain module contains controller, service, repository, entity, and dto. Global configuration and exception handling at project root level.

**Tech Stack:** Spring Boot 3.x, Spring Data JPA, MySQL, Redis, Lombok, Maven, JUnit 5

---

## File Structure Overview

**New files to create:**
```
pom.xml
.gitignore
.env
.env.example
src/main/java/com/interviewmirror/
  ├── InterviewMirrorApplication.java
  ├── config/
  │   ├── DatabaseConfig.java
  │   └── CacheConfig.java
  ├── exception/
  │   ├── BusinessException.java
  │   ├── ErrorResponse.java
  │   └── GlobalExceptionHandler.java
  ├── domain/user/
  │   ├── controller/UserController.java
  │   ├── service/UserService.java, UserServiceImpl.java
  │   ├── repository/UserRepository.java
  │   ├── entity/User.java
  │   └── dto/
  │       ├── UserCreateRequest.java
  │       ├── UserUpdateRequest.java
  │       └── UserResponse.java
  └── util/
      └── ApiResponse.java
src/main/resources/
  ├── application.yml
  ├── application-dev.yml
  ├── application-prod.yml
  └── schema.sql
src/test/java/com/interviewmirror/
  └── domain/user/
      ├── service/UserServiceTest.java
      └── repository/UserRepositoryTest.java
```

---

## Task 1: Create pom.xml

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: Create Maven configuration**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.4</version>
        <relativePath/>
    </parent>

    <groupId>com.interviewmirror</groupId>
    <artifactId>backend</artifactId>
    <version>1.0.0</version>
    <name>InterviewMirror Backend</name>
    <description>InterviewMirror Backend Service</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Spring Data Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MySQL Driver -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- SpringDoc OpenAPI (Swagger) -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.2.0</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- H2 for Testing -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Embedded Redis for Testing -->
        <dependency>
            <groupId>it.ozimov</groupId>
            <artifactId>embedded-redis</artifactId>
            <version>0.7.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Verify pom.xml is valid**

Run: `mvn clean validate`

Expected: Success with no errors

---

## Task 2: Create .gitignore and environment files

**Files:**
- Create: `.gitignore`
- Create: `.env`
- Create: `.env.example`

- [ ] **Step 1: Create .gitignore**

```
# IDE
.idea/
*.iml
.vscode/
*.swp
*.swo

# Maven
target/
*.jar
*.war
*.nar
*.ear
pom.xml.tag
pom.xml.releaseBackup

# Environment
.env
.env.local

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/
```

- [ ] **Step 2: Create .env.example**

```env
# Server
SERVER_PORT=8080

# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=interview_mirror
DB_USERNAME=root
DB_PASSWORD=

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

- [ ] **Step 3: Create .env**

```env
SERVER_PORT=8080
DB_HOST=localhost
DB_PORT=3306
DB_NAME=interview_mirror
DB_USERNAME=root
DB_PASSWORD=password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

- [ ] **Step 4: Commit**

```bash
git add pom.xml .gitignore .env.example .env
git commit -m "setup: add Maven configuration and environment files"
```

---

## Task 3: Create application entry point

**Files:**
- Create: `src/main/java/com/interviewmirror/InterviewMirrorApplication.java`

- [ ] **Step 1: Create Application class**

```java
package com.interviewmirror;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class InterviewMirrorApplication {
    public static void main(String[] args) {
        SpringApplication.run(InterviewMirrorApplication.class, args);
    }
}
```

- [ ] **Step 2: Verify file structure**

Run: `find src/main -name "InterviewMirrorApplication.java"`

Expected: Output shows `src/main/java/com/interviewmirror/InterviewMirrorApplication.java`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/interviewmirror/InterviewMirrorApplication.java
git commit -m "feat: add Spring Boot application entry point"
```

---

## Task 4: Create application configuration files

**Files:**
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-prod.yml`

- [ ] **Step 1: Create application.yml**

```yaml
server:
  port: ${SERVER_PORT:8080}
  servlet:
    context-path: /api

spring:
  application:
    name: interview-mirror-backend
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
        format_sql: true
  cache:
    type: redis
    redis:
      time-to-live: 1800000
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    timeout: 2000

logging:
  level:
    root: INFO
    com.interviewmirror: INFO

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 2: Create application-dev.yml**

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  cache:
    type: none

logging:
  level:
    root: INFO
    com.interviewmirror: DEBUG
    org.hibernate.SQL: DEBUG
```

- [ ] **Step 3: Create application-prod.yml**

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  cache:
    type: redis
    redis:
      time-to-live: 3600000

logging:
  level:
    root: WARN
    com.interviewmirror: INFO
```

- [ ] **Step 4: Verify files exist**

Run: `ls -la src/main/resources/application*.yml`

Expected: All three files listed

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/
git commit -m "config: add Spring application configuration files"
```

---

## Task 5: Create database configuration

**Files:**
- Create: `src/main/java/com/interviewmirror/config/DatabaseConfig.java`
- Create: `src/main/resources/schema.sql`

- [ ] **Step 1: Create DatabaseConfig.java**

```java
package com.interviewmirror.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {
    // Database configuration handled by Spring Boot auto-configuration
    // and application.yml properties
}
```

- [ ] **Step 2: Create schema.sql**

```sql
-- Create database if not exists
CREATE DATABASE IF NOT EXISTS interview_mirror CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE interview_mirror;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 3: Verify files exist**

Run: `find src/main -name "DatabaseConfig.java" && find src/main -name "schema.sql"`

Expected: Both files listed

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/interviewmirror/config/DatabaseConfig.java src/main/resources/schema.sql
git commit -m "config: add database configuration and schema"
```

---

## Task 6: Create cache configuration

**Files:**
- Create: `src/main/java/com/interviewmirror/config/CacheConfig.java`

- [ ] **Step 1: Create CacheConfig.java**

```java
package com.interviewmirror.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();

        return RedisCacheManager.create(connectionFactory);
    }
}
```

- [ ] **Step 2: Verify file exists**

Run: `find src/main -name "CacheConfig.java"`

Expected: File path shown

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/interviewmirror/config/CacheConfig.java
git commit -m "config: add Redis cache configuration"
```

---

## Task 7: Create exception handling

**Files:**
- Create: `src/main/java/com/interviewmirror/exception/BusinessException.java`
- Create: `src/main/java/com/interviewmirror/exception/ErrorResponse.java`
- Create: `src/main/java/com/interviewmirror/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create BusinessException.java**

```java
package com.interviewmirror.exception;

public class BusinessException extends RuntimeException {
    private final String errorCode;

    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BUSINESS_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
```

- [ ] **Step 2: Create ErrorResponse.java**

```java
package com.interviewmirror.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private String errorCode;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}
```

- [ ] **Step 3: Create GlobalExceptionHandler.java**

```java
package com.interviewmirror.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.error("Business exception: {}", e.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(e.getMessage())
                .errorCode(e.getErrorCode())
                .timestamp(LocalDateTime.now())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.error("Validation exception: {}", e.getMessage());
        
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid input");
        
        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(message)
                .errorCode("VALIDATION_ERROR")
                .timestamp(LocalDateTime.now())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected exception", e);
        
        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An unexpected error occurred")
                .errorCode("INTERNAL_SERVER_ERROR")
                .timestamp(LocalDateTime.now())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
```

- [ ] **Step 4: Verify files exist**

Run: `find src/main/java/com/interviewmirror/exception -name "*.java"`

Expected: All three files listed

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/interviewmirror/exception/
git commit -m "feat: add global exception handling"
```

---

## Task 8: Create User entity and repository

**Files:**
- Create: `src/main/java/com/interviewmirror/domain/user/entity/User.java`
- Create: `src/main/java/com/interviewmirror/domain/user/repository/UserRepository.java`

- [ ] **Step 1: Create User.java entity**

```java
package com.interviewmirror.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(nullable = false, length = 255)
    private String passwordHash;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create UserRepository.java**

```java
package com.interviewmirror.domain.user.repository;

import com.interviewmirror.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 3: Verify files exist**

Run: `find src/main -path "*domain/user*" -name "*.java" | head -5`

Expected: User.java and UserRepository.java listed

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/interviewmirror/domain/user/
git commit -m "feat: add User entity and repository"
```

---

## Task 9: Create User service layer

**Files:**
- Create: `src/main/java/com/interviewmirror/domain/user/service/UserService.java`
- Create: `src/main/java/com/interviewmirror/domain/user/service/UserServiceImpl.java`

- [ ] **Step 1: Create UserService.java interface**

```java
package com.interviewmirror.domain.user.service;

import com.interviewmirror.domain.user.dto.UserCreateRequest;
import com.interviewmirror.domain.user.dto.UserResponse;
import com.interviewmirror.domain.user.dto.UserUpdateRequest;

import java.util.List;

public interface UserService {
    UserResponse createUser(UserCreateRequest request);
    UserResponse getUserById(Long id);
    UserResponse getUserByEmail(String email);
    List<UserResponse> getAllUsers();
    UserResponse updateUser(Long id, UserUpdateRequest request);
    void deleteUser(Long id);
}
```

- [ ] **Step 2: Create UserServiceImpl.java**

```java
package com.interviewmirror.domain.user.service;

import com.interviewmirror.domain.user.dto.UserCreateRequest;
import com.interviewmirror.domain.user.dto.UserResponse;
import com.interviewmirror.domain.user.dto.UserUpdateRequest;
import com.interviewmirror.domain.user.entity.User;
import com.interviewmirror.domain.user.repository.UserRepository;
import com.interviewmirror.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists", "DUPLICATE_EMAIL");
        }
        
        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        
        User savedUser = userRepository.save(user);
        log.info("User created: {}", savedUser.getId());
        
        return mapToResponse(savedUser);
    }
    
    @Override
    @Cacheable(value = "user", key = "#id")
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        return mapToResponse(user);
    }
    
    @Override
    @Cacheable(value = "user", key = "#email")
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        return mapToResponse(user);
    }
    
    @Override
    @Cacheable(value = "users")
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    @CachePut(value = "user", key = "#id")
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        user.setName(request.getName());
        User updatedUser = userRepository.save(user);
        log.info("User updated: {}", updatedUser.getId());
        
        return mapToResponse(updatedUser);
    }
    
    @Override
    @Transactional
    @CacheEvict(value = {"user", "users"}, allEntries = true)
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException("User not found", "USER_NOT_FOUND");
        }
        userRepository.deleteById(id);
        log.info("User deleted: {}", id);
    }
    
    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 3: Verify files exist**

Run: `find src/main -path "*user/service*" -name "*.java"`

Expected: UserService.java and UserServiceImpl.java listed

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/interviewmirror/domain/user/service/
git commit -m "feat: add User service with caching"
```

---

## Task 10: Create User DTOs

**Files:**
- Create: `src/main/java/com/interviewmirror/domain/user/dto/UserCreateRequest.java`
- Create: `src/main/java/com/interviewmirror/domain/user/dto/UserUpdateRequest.java`
- Create: `src/main/java/com/interviewmirror/domain/user/dto/UserResponse.java`

- [ ] **Step 1: Create UserCreateRequest.java**

```java
package com.interviewmirror.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
```

- [ ] **Step 2: Create UserUpdateRequest.java**

```java
package com.interviewmirror.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {
    
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;
}
```

- [ ] **Step 3: Create UserResponse.java**

```java
package com.interviewmirror.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: Verify files exist**

Run: `find src/main -path "*user/dto*" -name "*.java"`

Expected: All three DTO files listed

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/interviewmirror/domain/user/dto/
git commit -m "feat: add User DTOs"
```

---

## Task 11: Create User controller

**Files:**
- Create: `src/main/java/com/interviewmirror/domain/user/controller/UserController.java`

- [ ] **Step 1: Create UserController.java**

```java
package com.interviewmirror.domain.user.controller;

import com.interviewmirror.domain.user.dto.UserCreateRequest;
import com.interviewmirror.domain.user.dto.UserResponse;
import com.interviewmirror.domain.user.dto.UserUpdateRequest;
import com.interviewmirror.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
        UserResponse response = userService.getUserByEmail(email);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> responses = userService.getAllUsers();
        return ResponseEntity.ok(responses);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Verify file exists**

Run: `find src/main -path "*user/controller*" -name "*.java"`

Expected: UserController.java listed

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/interviewmirror/domain/user/controller/
git commit -m "feat: add User REST controller"
```

---

## Task 12: Add BCryptPasswordEncoder bean

**Files:**
- Modify: `src/main/java/com/interviewmirror/config/DatabaseConfig.java`

- [ ] **Step 1: Update DatabaseConfig with PasswordEncoder**

```java
package com.interviewmirror.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DatabaseConfig {
    
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 2: Verify modification**

Run: `grep -n "BCryptPasswordEncoder" src/main/java/com/interviewmirror/config/DatabaseConfig.java`

Expected: Method definition visible

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/interviewmirror/config/DatabaseConfig.java
git commit -m "config: add BCryptPasswordEncoder bean"
```

---

## Task 13: Create User repository tests

**Files:**
- Create: `src/test/java/com/interviewmirror/domain/user/repository/UserRepositoryTest.java`

- [ ] **Step 1: Create test class**

```java
package com.interviewmirror.domain.user.repository;

import com.interviewmirror.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private UserRepository userRepository;
    
    private User testUser;
    
    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .name("Test User")
                .passwordHash("hashed_password")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    @Test
    void testSaveUser() {
        User saved = userRepository.save(testUser);
        
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getName()).isEqualTo("Test User");
    }
    
    @Test
    void testFindByEmail() {
        entityManager.persistAndFlush(testUser);
        
        Optional<User> found = userRepository.findByEmail("test@example.com");
        
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test User");
    }
    
    @Test
    void testFindByEmailNotFound() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");
        
        assertThat(found).isEmpty();
    }
    
    @Test
    void testExistsByEmail() {
        entityManager.persistAndFlush(testUser);
        
        boolean exists = userRepository.existsByEmail("test@example.com");
        
        assertThat(exists).isTrue();
    }
    
    @Test
    void testExistsByEmailNotFound() {
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");
        
        assertThat(exists).isFalse();
    }
    
    @Test
    void testDeleteById() {
        User saved = entityManager.persistAndFlush(testUser);
        
        userRepository.deleteById(saved.getId());
        
        Optional<User> deleted = userRepository.findById(saved.getId());
        assertThat(deleted).isEmpty();
    }
}
```

- [ ] **Step 2: Create application-test.yml**

```yaml
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
  cache:
    type: none
```

- [ ] **Step 3: Verify files exist**

Run: `find src/test -path "*user*" -name "*.java"`

Expected: UserRepositoryTest.java listed

- [ ] **Step 4: Run tests**

Run: `mvn test -Dtest=UserRepositoryTest`

Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add src/test/ src/main/resources/application-test.yml
git commit -m "test: add User repository tests with H2 in-memory database"
```

---

## Task 14: Create User service tests

**Files:**
- Create: `src/test/java/com/interviewmirror/domain/user/service/UserServiceTest.java`

- [ ] **Step 1: Create test class**

```java
package com.interviewmirror.domain.user.service;

import com.interviewmirror.domain.user.dto.UserCreateRequest;
import com.interviewmirror.domain.user.dto.UserResponse;
import com.interviewmirror.domain.user.dto.UserUpdateRequest;
import com.interviewmirror.domain.user.entity.User;
import com.interviewmirror.domain.user.repository.UserRepository;
import com.interviewmirror.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    
    @InjectMocks
    private UserServiceImpl userService;
    
    private User testUser;
    private UserCreateRequest createRequest;
    
    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .passwordHash("hashed_password")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        createRequest = UserCreateRequest.builder()
                .email("test@example.com")
                .name("Test User")
                .password("password123")
                .build();
    }
    
    @Test
    void testCreateUserSuccess() {
        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(createRequest.getPassword())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        UserResponse response = userService.createUser(createRequest);
        
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("Test User");
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void testCreateUserDuplicateEmail() {
        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(true);
        
        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email already exists");
        
        verify(userRepository, never()).save(any());
    }
    
    @Test
    void testGetUserByIdSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        
        UserResponse response = userService.getUserById(1L);
        
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("test@example.com");
    }
    
    @Test
    void testGetUserByIdNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        
        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found");
    }
    
    @Test
    void testGetUserByEmailSuccess() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        
        UserResponse response = userService.getUserByEmail("test@example.com");
        
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("test@example.com");
    }
    
    @Test
    void testGetAllUsers() {
        User user2 = User.builder()
                .id(2L)
                .email("test2@example.com")
                .name("Test User 2")
                .passwordHash("hashed_password")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(userRepository.findAll()).thenReturn(Arrays.asList(testUser, user2));
        
        List<UserResponse> responses = userService.getAllUsers();
        
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getEmail()).isEqualTo("test@example.com");
        assertThat(responses.get(1).getEmail()).isEqualTo("test2@example.com");
    }
    
    @Test
    void testUpdateUserSuccess() {
        UserUpdateRequest updateRequest = UserUpdateRequest.builder()
                .name("Updated User")
                .build();
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        UserResponse response = userService.updateUser(1L, updateRequest);
        
        assertThat(response).isNotNull();
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void testDeleteUserSuccess() {
        when(userRepository.existsById(1L)).thenReturn(true);
        
        userService.deleteUser(1L);
        
        verify(userRepository).deleteById(1L);
    }
    
    @Test
    void testDeleteUserNotFound() {
        when(userRepository.existsById(999L)).thenReturn(false);
        
        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found");
        
        verify(userRepository, never()).deleteById(any());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -Dtest=UserServiceTest`

Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/interviewmirror/domain/user/service/UserServiceTest.java
git commit -m "test: add User service unit tests"
```

---

## Task 15: Verify project builds and runs

**Files:**
- No new files, verification only

- [ ] **Step 1: Clean and build project**

Run: `mvn clean package -DskipTests`

Expected: Build success with no errors, target/backend-1.0.0.jar created

- [ ] **Step 2: Run all tests**

Run: `mvn test`

Expected: All tests pass (17 tests total for User domain)

- [ ] **Step 3: Start application**

Run: `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"`

Expected: Application starts successfully on port 8080, logs show:
```
Started InterviewMirrorApplication in X.XXX seconds
```

- [ ] **Step 4: Verify API endpoints**

Run: `curl -X GET http://localhost:8080/api/users`

Expected: Response `[]` (empty user list)

- [ ] **Step 5: Test create user endpoint**

Run: 
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","name":"John Doe","password":"password123"}'
```

Expected: Response with user data and status 201

- [ ] **Step 6: Verify Swagger UI**

Open: `http://localhost:8080/swagger-ui.html`

Expected: Swagger UI loads with User endpoints documented

- [ ] **Step 7: Verify Redis connection**

Check application logs for Redis connection messages.

Expected: No connection errors in logs

- [ ] **Step 8: Final commit**

```bash
git add -A
git commit -m "init: complete Spring Boot project initialization with User domain example"
```

---

## Success Criteria

✅ All project files created with correct structure
✅ Maven builds successfully
✅ All 17+ tests pass
✅ Application starts without errors
✅ REST API endpoints respond correctly
✅ Caching configuration verified
✅ Exception handling works as expected
✅ Database connectivity confirmed
✅ Swagger documentation generated
✅ Environment variables properly configured

---

## Summary

This plan creates a production-ready Spring Boot backend with:
- Domain-driven architecture (user domain as example)
- MySQL database integration
- Redis caching layer
- Comprehensive exception handling
- Full REST API for User operations
- Unit and integration tests
- Development-ready configuration

Each new domain can follow the same structure under `domain/`, making this a scalable template for future development.
