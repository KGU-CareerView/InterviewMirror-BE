package com.interviewmirror.interview.controller;

import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.infrastructure.S3Service;
import com.interviewmirror.interview.dto.*;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.service.InterviewPreparationService;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.interview.service.SessionService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/sessions")
public class SessionController {

  private final SessionService sessionService;
  private final InterviewPreparationService preparationService;
  private final RedisSessionService redisSessionService;
  private final S3Service s3Service;

  // 면접 세션 생성 및 초기화
  @PostMapping
  public ResponseEntity<ApiResponse<SessionCreateResponse>> createSession(
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    // 토큰에서 검증된 유저 ID를 안전하게 꺼냅니다.
    Long safeUserId = userDetails.getId();

    Long generatedSessionId = sessionService.createSession(safeUserId);

    SessionCreateResponse responseDto =
        SessionCreateResponse.builder()
            .sessionId(generatedSessionId)
            .date(LocalDateTime.now().plusMinutes(30))
            .sessionState(InterviewSessionState.READY.name())
            .build();

    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(responseDto));
  }

  // 면접 시작: 설정 저장, 세션 시작 전환, 초기 질문 생성 요청을 하나의 유스케이스로 처리
  @PostMapping("/{sessionID}/start")
  public ResponseEntity<ApiResponse<InterviewSettingResponse>> startInterview(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable("sessionID") Long sessionID,
      @Valid @RequestBody InterviewSettingRequest request) {

    Long userId = userDetails.getId();
    InterviewSettingResponse response = preparationService.saveSetting(sessionID, userId, request);

    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
  }

  // 면접 세션 상태 변경 (PAUSED, IN_PROGRESS, ENDED)
  @PatchMapping("/{sessionID}/status")
  public ResponseEntity<ApiResponse<Map<String, String>>> updateSessionStatus(
      @AuthenticationPrincipal CustomUserDetails userDetails, // 1. 보안 토큰 파라미터 추가
      @PathVariable("sessionID") Long sessionID,
      @RequestBody SessionStatusUpdateRequest request) {

    // 검증된 유저 ID 추출
    Long userId = userDetails.getId();
    String newStatus = request.getStatus();

    if (InterviewSessionState.PREPARING.name().equalsIgnoreCase(newStatus)
        || InterviewSessionState.READY.name().equalsIgnoreCase(newStatus)) {
      throw new InterviewException(ErrorCode.VALIDATION_ERROR);
    }

    // Service로 userId를 함께 전달
    sessionService.changeState(sessionID, userId, newStatus);

    return ResponseEntity.ok(ApiResponse.success(Map.of("Result", "SUCCESS")));
  }

  // 현재 면접 세션 상태 조회
  @GetMapping("/{sessionID}")
  public ResponseEntity<ApiResponse<SessionStateResponse>> getSessionState(
      @PathVariable("sessionID") Long sessionID) {

    String state = redisSessionService.getSessionState(sessionID);
    if (state == null) throw new InterviewException(ErrorCode.SESSION_EXPIRED);

    SessionStateResponse responseDto = SessionStateResponse.builder().sessionState(state).build();

    // ApiResponse 적용
    return ResponseEntity.ok(ApiResponse.success(responseDto));
  }

  // S3 업로드용 Presigned URL 발급
  @PostMapping("/{sessionID}/presigned")
  public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
      @PathVariable("sessionID") Long sessionID, @RequestBody PresignedUrlRequest request) {

    String fileType = request.getFileType() != null ? request.getFileType() : "mp4";

    String presignedUrl = s3Service.generatePresignedUrl(sessionID, fileType);

    PresignedUrlResponse responseDto =
        PresignedUrlResponse.builder().presignedUrl(presignedUrl).build();

    return ResponseEntity.ok(ApiResponse.success(responseDto));
  }

  // 생성된 미디어(영상, 이미지, 음성) URL DB 저장
  @PostMapping("/{sessionID}/save")
  public ResponseEntity<ApiResponse<Map<String, String>>> saveMediaUrl(
      @AuthenticationPrincipal CustomUserDetails userDetails, // 로그인 유저 정보 추출
      @PathVariable("sessionID") Long sessionID,
      @RequestBody MediaSaveRequest request) {

    Long userId = userDetails.getId(); // 유저 ID 추출
    String contentUrl = request.getContentUrl();

    String mediaType = request.getType();

    // 비디오 외의 타입이 들어오면 확실하게 에러 처리
    if (!"video".equalsIgnoreCase(mediaType)) {
      log.warn("지원하지 않는 미디어 타입 저장 시도 - SessionID: {}, Type: {}", sessionID, mediaType);

      throw new InterviewException(ErrorCode.INVALID_REQUEST);
    }

    sessionService.saveVideoUrl(sessionID, userId, contentUrl);

    return ResponseEntity.ok(ApiResponse.success(Map.of("Result", "SUCCESS")));
  }
}
