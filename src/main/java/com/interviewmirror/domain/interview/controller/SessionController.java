package com.interviewmirror.domain.interview.controller;

import lombok.RequiredArgsConstructor;
import com.interviewmirror.exception.InterviewException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.config.AiGrpcClient;
import com.interviewmirror.config.S3Service;
import com.interviewmirror.domain.interview.service.RedisSessionService;
import com.interviewmirror.domain.interview.service.SessionService;
import com.interviewmirror.domain.interview.dto.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/{userID}/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final RedisSessionService redisSessionService;
    private final S3Service s3Service;
    private final AiGrpcClient aiGrpcClient;
    private final SimpMessagingTemplate messagingTemplate;

    // 1. 면접 세션 생성 및 초기화
    @PostMapping
    public ResponseEntity<SessionCreateResponse> createSession(@PathVariable("userID") long userID) {
        // DB와 Redis에 세션을 생성하고 ID를 반환받는 서비스 로직 호출
        Long generatedSessionId = sessionService.createSession(userID);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
                SessionCreateResponse.builder()
                        .sessionId(generatedSessionId)
                        .date(LocalDateTime.now().plusMinutes(30)) // 예시: 30분 유효기간
                        .sessionState("INIT") // 초기 세션 상태
                        .build()
        );
    }

    // 2. 면접 세션 상태 변경 (START, PAUSE, RESUME, END)
    @PatchMapping("/{sessionID}/status")
    public ResponseEntity<Map<String, String>> updateSessionStatus(
            @PathVariable("sessionID") Long sessionID, 
            @RequestBody SessionStatusUpdateRequest request) {
        
        // DTO를 사용하여 상태값을 안전하게 추출
        String newStatus = request.getStatus();
        
        // 세션 상태 변경 로직 호출 (메서드명은 실제 구현에 맞게 수정)
        sessionService.changeState(sessionID, newStatus);
        
        return ResponseEntity.ok(Map.of("Result", "SUCCESS"));
    }

    // 3. 현재 면접 세션 상태 조회
    @GetMapping("/{sessionID}")
    public ResponseEntity<SessionStateResponse> getSessionState(@PathVariable("sessionID") Long sessionID) {
        String state = redisSessionService.getSessionState(sessionID);
        if (state == null) throw new InterviewException(ErrorCode.SESSION_EXPIRED);
        
        return ResponseEntity.ok(
                SessionStateResponse.builder()
                        .sessionState(state)
                        .build()
        );
    }

    // 4. S3 업로드용 Presigned URL 발급
    @PostMapping("/{sessionID}/presigned")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @PathVariable("sessionID") Long sessionID, 
            @RequestBody PresignedUrlRequest request) {
    
        String fileType = request.getFileType() != null ? request.getFileType() : "video/mp4";
        String ext = fileType.contains("mp4") ? "mp4" : "png";

        String presignedUrl = s3Service.generatePresignedUrl(sessionID, fileType);
        
        return ResponseEntity.ok(
                PresignedUrlResponse.builder()
                        .presignedUrl(presignedUrl)
                        .build()
        );
    }

    
    // 5. 생성된 미디어(영상, 이미지, 음성) URL DB 저장
    
    @PostMapping("/{sessionID}/save/{mediaType}")
    public ResponseEntity<Map<String, String>> saveMediaUrl(
            @PathVariable("sessionID") Long sessionID, 
            @PathVariable("mediaType") String mediaType, 
            @RequestBody MediaSaveRequest request) {
        
        String contentUrl = request.getContentUrl();
        if ("video".equalsIgnoreCase(mediaType)) {
            sessionService.saveVideoUrl(sessionID, contentUrl);
        }
        
        return ResponseEntity.ok(Map.of("Result", "SUCCESS"));
    }

    
    // 6. 사용자 답변 제출 및 다음 AI 질문 생성 요청
    @PostMapping("/{sessionID}/answer")
    public ResponseEntity<Map<String, String>> submitAnswer(
            @PathVariable("sessionID") Long sessionID,
            @RequestBody AnswerSubmitRequest request) {

        // 프론트엔드에서는 답변만 받아옵니다.
        String answer = request.getAnswer();

        // 파라미터를 2개(sessionID, answer)만 넘기도록 원상복구 합니다.
        sessionService.processAnswerAndGenerateQuestion(sessionID, answer);

        return ResponseEntity.accepted().body(Map.of("Result", "ok"));
    }

    
    // 7. 실시간 감정 데이터 분석 요청 (gRPC & WebSocket)
    @PostMapping("/{sessionID}/emotion")
    public ResponseEntity<EmotionDataResponse> analyzeEmotion(
            @PathVariable("sessionID") Long sessionID, 
            @RequestBody EmotionDataRequest request) {
        
        String facialData = request.getData();

        // gRPC 통신으로 AI 분석 요청
        String emotionResult = aiGrpcClient.analyzeEmotion(sessionID, facialData);

        // WebSocket으로 실시간 결과 브로드캐스팅
        messagingTemplate.convertAndSend("/topic/session/" + sessionID + "/emotion", Map.of(
                "type", "EMOTION_UPDATE",
                "emotion", emotionResult
        ));

        return ResponseEntity.ok(
                EmotionDataResponse.builder()
                        .message("전달완료")
                        .result(emotionResult)
                        .build()
        );
    }
}
