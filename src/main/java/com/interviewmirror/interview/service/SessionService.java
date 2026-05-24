package com.interviewmirror.interview.service;

import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.infrastructure.S3Service;
import com.interviewmirror.interview.dto.MediaSaveRequest;
import com.interviewmirror.interview.dto.PresignedUrlRequest;
import com.interviewmirror.interview.dto.PresignedUrlResponse;
import com.interviewmirror.interview.dto.SessionCreateResponse;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import com.interviewmirror.realtime.dto.AudioSummaryDto;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {
  private final InterviewResultRepository resultRepository;
  private final InterviewDetailRepository detailRepository;
  private final RedisSessionService redisSessionService;
  private final AudioScoreService audioScoreService;
  private final S3Service s3Service;

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

  @Transactional
  public String recordAnswer(
      Long sessionId,
      String answer,
      String emotionResult,
      Integer responseTimeSeconds,
      AudioSummaryDto audioSummary) {
    String question = redisSessionService.getLastQuestion(sessionId);
    String safeQuestion = (question != null) ? question : "";

    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_NOT_FOUND));

    detailRepository.save(
        InterviewDetail.builder()
            .interviewResult(result)
            .qId(System.currentTimeMillis())
            .question(safeQuestion)
            .answer(answer)
            .emotionResult(emotionResult)
            .responseTimeSeconds(responseTimeSeconds)
            .audioSummaryJson(audioScoreService.serializeSummary(audioSummary))
            .audioScore(audioScoreService.calculateQuestionScore(audioSummary))
            .build());

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

  @Transactional(readOnly = true)
  public InterviewResult getValidatedSession(Long sessionId, Long userId) {
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_NOT_FOUND));

    if (!result.getUserId().equals(userId)) {
      log.warn("[보안 경고] 타인 세션 접근 시도 - SessionId: {}, UserId: {}", sessionId, userId);
      throw new InterviewException(ErrorCode.AUTH_UNAUTHORIZED);
    }

    return result;
  }
}
