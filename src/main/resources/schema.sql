-- -- Create database if not exists
-- CREATE DATABASE IF NOT EXISTS interview_mirror CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--
-- USE interview_mirror;
--
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

CREATE TABLE IF NOT EXISTS feedback_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    total_frames INT NOT NULL,
    dominant_label VARCHAR(50) NOT NULL,
    average_confidence DOUBLE NOT NULL,
    stable_count INT NOT NULL,
    nervous_count INT NOT NULL,
    neutral_count INT NOT NULL,
    face_detected_count INT NOT NULL,
    latest_feedback VARCHAR(1000),
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feedback_results_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
