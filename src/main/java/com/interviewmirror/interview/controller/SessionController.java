package com.interviewmirror.interview.controller;

import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.interview.dto.*;
import com.interviewmirror.interview.service.InterviewPreparationService;
import com.interviewmirror.interview.service.SessionService;
import com.interviewmirror.interview.service.SessionStateService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/sessions")
public class SessionController {

  private final SessionService sessionService;
  private final SessionStateService sessionStateService;
  private final InterviewPreparationService preparationService;

  // 면접 세션 생성 및 초기화
  @PostMapping
  public ResponseEntity<ApiResponse<SessionCreateResponse>> createSession(
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    SessionCreateResponse response = sessionService.createSession(userDetails.getId());

    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
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
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable("sessionID") Long sessionID,
      @RequestBody SessionStatusUpdateRequest request) {

    sessionStateService.changeState(sessionID, userDetails.getId(), request.getStatus());

    return ResponseEntity.ok(ApiResponse.success(Map.of("Result", "SUCCESS")));
  }

  // 현재 면접 세션 상태 조회
  @GetMapping("/{sessionID}")
  public ResponseEntity<ApiResponse<SessionStateResponse>> getSessionState(
      @PathVariable("sessionID") Long sessionID) {

    SessionStateResponse response = sessionStateService.getSessionState(sessionID);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // S3 업로드용 Presigned URL 발급
  @PostMapping("/{sessionID}/presigned")
  public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
      @PathVariable("sessionID") Long sessionID, @RequestBody PresignedUrlRequest request) {

    PresignedUrlResponse response = sessionService.createPresignedUrl(sessionID, request);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // 생성된 미디어(영상, 이미지, 음성) URL DB 저장
  @PostMapping("/{sessionID}/save")
  public ResponseEntity<ApiResponse<Map<String, String>>> saveMediaUrl(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable("sessionID") Long sessionID,
      @RequestBody MediaSaveRequest request) {

    sessionService.saveMediaUrl(sessionID, userDetails.getId(), request);

    return ResponseEntity.ok(ApiResponse.success(Map.of("Result", "SUCCESS")));
  }
}
