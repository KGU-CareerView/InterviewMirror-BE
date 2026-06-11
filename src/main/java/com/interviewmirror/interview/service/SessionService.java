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

  private static final String FRONTEND_PLACEHOLDER_ANSWER = "사용자가 답변을 완료했습니다.";

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
      String question,
      String answer,
      String emotionResult,
      Integer responseTimeSeconds,
      AudioSummaryDto audioSummary) {
    String resolvedQuestion =
        (question != null && !question.isBlank())
            ? question
            : redisSessionService.getLastQuestion(sessionId);
    String safeQuestion = (resolvedQuestion != null) ? resolvedQuestion : "";

    String safeAnswer = sanitizePlaceholder(sessionId, answer);
    logSttQuality(sessionId, safeAnswer, responseTimeSeconds);

    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_NOT_FOUND));

    detailRepository.save(
        InterviewDetail.builder()
            .interviewResult(result)
            .qId(System.currentTimeMillis())
            .question(safeQuestion)
            .answer(safeAnswer)
            .emotionResult(emotionResult)
            .responseTimeSeconds(responseTimeSeconds)
            .audioSummaryJson(audioScoreService.serializeSummary(audioSummary))
            .audioScore(audioScoreService.calculateQuestionScore(audioSummary))
            .build());

    return safeQuestion;
  }

  private String sanitizePlaceholder(Long sessionId, String answer) {
    if (answer != null && FRONTEND_PLACEHOLDER_ANSWER.equals(answer.trim())) {
      log.warn(
          "[STT] 누적 transcript도 없어 프론트 placeholder가 그대로 도달 - 빈 문자열로 저장 sessionId={}", sessionId);
      return "";
    }
    return answer == null ? "" : answer;
  }

  private void logSttQuality(Long sessionId, String answer, Integer responseTimeSeconds) {
    int chars = answer == null ? 0 : answer.length();
    int words = (answer == null || answer.isBlank()) ? 0 : answer.trim().split("\\s+").length;
    int seconds = responseTimeSeconds == null ? 0 : responseTimeSeconds;
    String charsPerSec = seconds > 0 ? String.format("%.2f", (double) chars / seconds) : "n/a";

    if (chars == 0) {
      log.warn("[STT] 빈 답변 수신 - STT 실패 가능성 sessionId={} responseTimeSec={}", sessionId, seconds);
      return;
    }
    if (chars < 10 || (seconds >= 5 && chars < seconds * 2)) {
      log.warn(
          "[STT] 답변이 비정상적으로 짧음 - STT 누락 의심 sessionId={} chars={} words={} responseTimeSec={} charsPerSec={} answer='{}'",
          sessionId,
          chars,
          words,
          seconds,
          charsPerSec,
          answer);
      return;
    }
    log.info(
        "[STT] 답변 수신 정상 sessionId={} chars={} words={} responseTimeSec={} charsPerSec={} preview='{}'",
        sessionId,
        chars,
        words,
        seconds,
        charsPerSec,
        previewAnswer(answer));
  }

  private String previewAnswer(String value) {
    if (value == null || value.isBlank()) return "";
    return value.length() <= 80 ? value : value.substring(0, 80) + "...";
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
