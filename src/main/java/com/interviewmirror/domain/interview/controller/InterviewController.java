package com.interviewmirror.domain.interview.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.interviewmirror.domain.interview.dto.InterviewResultResponse;
import com.interviewmirror.domain.interview.dto.InterviewHistoryResponse;
import com.interviewmirror.domain.interview.service.InterviewService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews")
public class InterviewController {

    private final InterviewService interviewService; // 서비스 계층 주입

    // 8. 결과 조회
    @GetMapping("/{sessionID}/result")
    public ResponseEntity<InterviewResultResponse> getResult(@PathVariable("sessionID") Long sessionID) {
        String resultData = interviewService.getInterviewResult(sessionID);
        
        return ResponseEntity.ok(
                InterviewResultResponse.builder()
                        .result(resultData)
                        .build()
        );
    }

    // 9. 사용자 과거 기록 조회
    @GetMapping("/history")
    public ResponseEntity<InterviewHistoryResponse> getHistory(@RequestAttribute("userId") Long userId) {
        // DB에서 조회해온 세션 ID 리스트를 DTO에 담아 반환
        return ResponseEntity.ok(
                InterviewHistoryResponse.builder()
                        .sessionIds(interviewService.getHistory(userId))
                        .build()
        );
    }
}
