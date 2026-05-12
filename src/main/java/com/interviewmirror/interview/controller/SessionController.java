package com.interviewmirror.interview.controller;

import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.S3Service;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.interview.dto.*;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.interview.service.SessionService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions")
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

    // ApiResponse 봉투에 담아서 반환
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(responseDto));
  }

  // 면접 세션 상태 변경 (START, PAUSE, RESUME, END)
  @PatchMapping("/{sessionID}/status")
  public ResponseEntity<ApiResponse<Map<String, String>>> updateSessionStatus(
      @PathVariable("sessionID") Long sessionID, @RequestBody SessionStatusUpdateRequest request) {

    String newStatus = request.getStatus();
    sessionService.changeState(sessionID, newStatus);

    // ApiResponse 적용
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

    String fileType = request.getFileType() != null ? request.getFileType() : "video/mp4";
    // 🧹 쓰지 않는 ext 변수 삭제 완료

    String presignedUrl = s3Service.generatePresignedUrl(sessionID, fileType);

    PresignedUrlResponse responseDto =
        PresignedUrlResponse.builder().presignedUrl(presignedUrl).build();

    // ApiResponse 적용
    return ResponseEntity.ok(ApiResponse.success(responseDto));
  }

  // 생성된 미디어(영상, 이미지, 음성) URL DB 저장
  @PostMapping("/{sessionID}/save/{mediaType}")
  public ResponseEntity<ApiResponse<Map<String, String>>> saveMediaUrl(
      @PathVariable("sessionID") Long sessionID,
      @PathVariable("mediaType") String mediaType,
      @RequestBody MediaSaveRequest request) {

    String contentUrl = request.getContentUrl();
    if ("video".equalsIgnoreCase(mediaType)) {
      sessionService.saveVideoUrl(sessionID, contentUrl);
    }

    // ApiResponse 적용
    return ResponseEntity.ok(ApiResponse.success(Map.of("Result", "SUCCESS")));
  }

  // 사용자 답변 제출 및 다음 AI 질문 생성 요청
  @PostMapping("/{sessionID}/answer")
  public ResponseEntity<ApiResponse<Map<String, String>>> submitAnswer(
      @PathVariable("sessionID") Long sessionID, @RequestBody AnswerSubmitRequest request) {

    String answer = request.getAnswer();
    sessionService.processAnswerAndGenerateQuestion(sessionID, answer);

    // ApiResponse 적용 (202 Accepted)
    return ResponseEntity.accepted().body(ApiResponse.success(Map.of("Result", "ok")));
  }

  // 실시간 감정 데이터 분석 요청 (gRPC & WebSocket)
  @PostMapping("/{sessionID}/emotion")
  public ResponseEntity<ApiResponse<EmotionDataResponse>> analyzeEmotion(
      @PathVariable("sessionID") Long sessionID, @RequestBody EmotionDataRequest request) {

    String facialData = request.getData();

    // gRPC 통신으로 AI 분석 요청
    String emotionResult = aiGrpcClient.analyzeEmotion(sessionID, facialData);

    // WebSocket으로 실시간 결과 브로드캐스팅
    messagingTemplate.convertAndSend(
        "/topic/session/" + sessionID + "/emotion",
        Map.of("type", "EMOTION_UPDATE", "emotion", emotionResult));

    EmotionDataResponse responseDto =
        EmotionDataResponse.builder().message("전달완료").result(emotionResult).build();

    // ApiResponse 적용
    return ResponseEntity.ok(ApiResponse.success(responseDto));
  }
}
