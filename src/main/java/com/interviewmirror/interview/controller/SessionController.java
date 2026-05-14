package com.interviewmirror.interview.controller;

import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.infrastructure.AiGrpcClient;
import com.interviewmirror.infrastructure.S3Service;
import com.interviewmirror.interview.dto.*;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.interview.service.SessionService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/sessions")
public class SessionController {

  private final SessionService sessionService;
  private final RedisSessionService redisSessionService;
  private final S3Service s3Service;
  private final AiGrpcClient aiGrpcClient;
  private final SimpMessagingTemplate messagingTemplate;

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
            .sessionState("INIT")
            .build();

    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(responseDto));
  }

  // 면접 세션 상태 변경 (START, PAUSE, RESUME, END)
  @PatchMapping("/{sessionID}/status")
  public ResponseEntity<ApiResponse<Map<String, String>>> updateSessionStatus(
      @AuthenticationPrincipal CustomUserDetails userDetails, // 1. 보안 토큰 파라미터 추가
      @PathVariable("sessionID") Long sessionID,
      @RequestBody SessionStatusUpdateRequest request) {

    // 검증된 유저 ID 추출
    Long userId = userDetails.getId();
    String newStatus = request.getStatus();

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

  // 사용자 답변 제출 및 다음 AI 질문 생성 요청
  @PostMapping("/{sessionID}/answer")
  public ResponseEntity<ApiResponse<Void>> submitAnswer(
      @PathVariable("sessionID") Long sessionID, @RequestBody AnswerSubmitRequest request) {

    sessionService.processAnswerAndGenerateQuestion(
        sessionID,
        request.getAnswer(),
        request.getEmotionResult(),
        request.getResponseTimeSeconds());

    return ResponseEntity.ok(ApiResponse.success(null));
  }

  // 실시간 감정 데이터 분석 요청 (gRPC & WebSocket)
  @PostMapping("/{sessionID}/emotion")
  public ResponseEntity<ApiResponse<EmotionDataResponse>> analyzeEmotion(
      @PathVariable("sessionID") Long sessionID, @RequestBody EmotionDataRequest request) {

    // 서비스 계층으로 핵심 로직 위임
    String emotionResult = sessionService.processAndBroadcastEmotion(sessionID, request.getData());

    // 응답 DTO 생성
    EmotionDataResponse responseDto =
        EmotionDataResponse.builder().message("전달완료").result(emotionResult).build();

    return ResponseEntity.ok(ApiResponse.success(responseDto));
  }
}
