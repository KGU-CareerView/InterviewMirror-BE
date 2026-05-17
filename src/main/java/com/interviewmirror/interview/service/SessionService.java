package com.interviewmirror.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.infrastructure.S3Service;
import com.interviewmirror.interview.dto.MediaSaveRequest;
import com.interviewmirror.interview.dto.PresignedUrlRequest;
import com.interviewmirror.interview.dto.PresignedUrlResponse;
import com.interviewmirror.interview.dto.SessionCreateResponse;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {
  private final InterviewResultRepository resultRepository;
  private final RedisSessionService redisSessionService;

  private final S3Service s3Service;
  private final ObjectMapper objectMapper; // JSON 직렬화용

  @Transactional
  public SessionCreateResponse createSession(Long userId) {
    InterviewResult savedResult =
        resultRepository.save(
            InterviewResult.builder()
                .userId(userId)
                .sessionState(InterviewSessionState.READY.name())
                .createTime(LocalDateTime.now())
                .build());

    Long generatedSessionId = savedResult.getSessionId();
    redisSessionService.updateSessionState(generatedSessionId, InterviewSessionState.READY.name());
    return SessionCreateResponse.builder()
        .sessionId(generatedSessionId)
        .date(LocalDateTime.now().plusMinutes(30))
        .sessionState(InterviewSessionState.READY.name())
        .build();
  }

  public String recordAnswer(
      Long sessionId, String answer, String emotionResult, Integer responseTimeSeconds) {
    String question = redisSessionService.getLastQuestion(sessionId);
    String safeQuestion = (question != null) ? question : "";

    try {
      Map<String, Object> qaData = new HashMap<>();
      qaData.put("quizID", System.currentTimeMillis());
      qaData.put("question", safeQuestion);
      qaData.put("answer", answer);
      qaData.put("emotionResult", emotionResult);
      qaData.put("responseTimeSeconds", responseTimeSeconds);

      String qaJson = objectMapper.writeValueAsString(qaData);
      redisSessionService.addQaToRedis(sessionId, qaJson);
    } catch (Exception e) {
      log.error("Redis 문답 JSON 직렬화 실패: {}", e.getMessage());
      throw new InterviewException(ErrorCode.SERVER_INTERNAL_ERROR);
    }

    return safeQuestion;
  }

  public PresignedUrlResponse createPresignedUrl(Long sessionId, PresignedUrlRequest request) {
    String fileType =
        request != null && request.getFileType() != null ? request.getFileType() : "mp4";
    String presignedUrl = s3Service.generatePresignedUrl(sessionId, fileType);

    return PresignedUrlResponse.builder().presignedUrl(presignedUrl).build();
  }

  @Transactional
  public void saveMediaUrl(Long sessionId, Long userId, MediaSaveRequest request) {
    if (!"video".equalsIgnoreCase(request.getType())) {
      log.warn("지원하지 않는 미디어 타입 저장 시도 - SessionID: {}, Type: {}", sessionId, request.getType());
      throw new InterviewException(ErrorCode.INVALID_REQUEST);
    }

    InterviewResult result = getValidatedSession(sessionId, userId);
    result.setVideoUrl(request.getContentUrl());
  }

  /**
   * [공통 검증 로직] 세션 ID로 DB에서 세션을 찾고, 요청한 유저의 소유가 맞는지 검증합니다. 검증에 성공하면 InterviewResult 객체를 반환하고, 실패하면
   * 예외를 던집니다.
   */
  @Transactional(readOnly = true)
  public InterviewResult getValidatedSession(Long sessionId, Long userId) {
    // 1. 세션 존재 여부 확인
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_NOT_FOUND));

    // 2. 소유권 검증 (IDOR 방어)
    if (!result.getUserId().equals(userId)) {
      log.warn("[보안 경고] 타인 세션 접근 시도 - SessionId: {}, UserId: {}", sessionId, userId);
      throw new InterviewException(ErrorCode.AUTH_UNAUTHORIZED);
    }

    return result;
  }
}
