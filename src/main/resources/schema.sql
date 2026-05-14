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

-- 면접 사전 설정 테이블 (Interview Settings)
CREATE TABLE IF NOT EXISTS interview_settings (
                                                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                  user_id BIGINT NOT NULL,
                                                  category VARCHAR(100),          -- 분야 (예: 개발, 마케팅)
    interview_type VARCHAR(50),     -- 유형 (인성, 직무, 종합)
    difficulty VARCHAR(20),         -- 난이도 (상, 중, 하)
    question_count INT DEFAULT 5,   -- 질문 개수
    time_per_question INT,          -- 질문당 시간 설정
    resume_content LONGTEXT,        -- 첨부한 자소서 내용
    jd_content LONGTEXT,            -- 첨부한 JD 내용
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 면접 결과 요약 테이블 (Interview Results)
CREATE TABLE IF NOT EXISTS interview_results (
                                                 session_id BIGINT PRIMARY KEY AUTO_INCREMENT, -- 세션 식별자 (PK)
                                                 user_id BIGINT NOT NULL,
                                                 session_state VARCHAR(20),      -- 세션 상태 (INIT, START, END 등)
    video_url VARCHAR(255),         -- S3 비디오 경로
    emotion_graph_json LONGTEXT,    -- 전체 감정 변화 데이터 (JSON 형태)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_results_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 면접 상세 문답 테이블 (Interview Details)
CREATE TABLE IF NOT EXISTS interview_details (
                                                 id BIGINT PRIMARY KEY AUTO_INCREMENT,        -- 문답 번호 (PK)
                                                 session_id BIGINT NOT NULL,                  -- 소속 세션 ID (FK)
                                                 question_text TEXT NOT NULL,                 -- 질문 내용
                                                 answer_text TEXT,                            -- 사용자 답변 내용
                                                 emotion_result VARCHAR(50),                  -- 해당 답변 시 감정 분석 결과
    response_time_seconds INT,                   -- 답변에 소요된 시간
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_details_session FOREIGN KEY (session_id) REFERENCES interview_results(session_id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 면접 최종 평가 리포트 테이블 (Interview Reports)
CREATE TABLE IF NOT EXISTS interview_reports (
                                                 session_id BIGINT NOT NULL,                  -- InterviewResult의 sessionId를 그대로 PK이자 FK로 사용
                                                 total_score INT NOT NULL,
                                                 feedback TEXT,
                                                 strengths TEXT,
                                                 weaknesses TEXT,
                                                 ai_analysis_json JSON,
                                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- 다른 테이블과 네이밍 컨벤션 통일
                                                 PRIMARY KEY (session_id),
    CONSTRAINT fk_report_session_id FOREIGN KEY (session_id) REFERENCES interview_results(session_id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;