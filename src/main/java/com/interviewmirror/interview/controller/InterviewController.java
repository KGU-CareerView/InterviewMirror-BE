package com.interviewmirror.interview.controller;

import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.interview.dto.InterviewHistoryResponse;
import com.interviewmirror.interview.dto.InterviewReportResponse;
import com.interviewmirror.interview.dto.InterviewResultResponse;
import com.interviewmirror.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/interviews")
public class InterviewController {

  private final InterviewService interviewService;

  @GetMapping("/{sessionId}/result")
  public ResponseEntity<ApiResponse<InterviewResultResponse>> getResult(
      @PathVariable("sessionId") Long sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    Long userId = userDetails.getId();

    InterviewResultResponse response = interviewService.getInterviewResult(sessionId, userId);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/history")
  public ResponseEntity<ApiResponse<InterviewHistoryResponse>> getHistory(
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    Long userId = userDetails.getId();

    return ResponseEntity.ok(
        ApiResponse.success(
            InterviewHistoryResponse.builder()
                .sessionIds(interviewService.getHistory(userId))
                .build()));
  }

  @GetMapping("/{sessionId}/report")
  public ResponseEntity<ApiResponse<InterviewReportResponse>> getInterviewReport(
      @PathVariable("sessionId") Long sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    // 서비스에서 리포트 데이터 가져오기
    InterviewReportResponse response =
        interviewService.getInterviewReport(sessionId, userDetails.getId());

    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
