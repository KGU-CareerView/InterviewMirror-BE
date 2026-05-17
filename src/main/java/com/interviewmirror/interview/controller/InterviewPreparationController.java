package com.interviewmirror.interview.controller;

import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.interview.dto.InterviewSettingDetailResponse;
import com.interviewmirror.interview.service.InterviewPreparationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/preparation")
public class InterviewPreparationController {

  private final InterviewPreparationService preparationService;

  @GetMapping("/settings/{sessionId}")
  public ResponseEntity<ApiResponse<InterviewSettingDetailResponse>> getSettingBySessionId(
      @PathVariable("sessionId") Long sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    Long userId = userDetails.getId();
    InterviewSettingDetailResponse response =
        preparationService.getSettingBySessionId(sessionId, userId);

    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
