package com.interviewmirror.interview.controller;

import com.interviewmirror.auth.security.CustomUserDetails;
import com.interviewmirror.common.ApiResponse;
import com.interviewmirror.interview.dto.AnswerTipRequest;
import com.interviewmirror.interview.dto.AnswerTipResponse;
import com.interviewmirror.interview.dto.InterviewSettingDetailResponse;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.dto.InterviewSettingResponse;
import com.interviewmirror.interview.service.InterviewPreparationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/preparation")
public class InterviewPreparationController {

  private final InterviewPreparationService preparationService;

  @PostMapping("/settings/save/{sessionId}")
  public ResponseEntity<ApiResponse<InterviewSettingResponse>> saveInterviewSetting(
      @PathVariable("sessionId") Long sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody InterviewSettingRequest request) {

    Long userId = userDetails.getId();
    Long settingId = preparationService.saveSetting(sessionId, userId, request);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(InterviewSettingResponse.builder().settingId(settingId).build()));
  }

  @GetMapping("/settings/{sessionId}")
  public ResponseEntity<ApiResponse<InterviewSettingDetailResponse>> getSettingBySessionId(
      @PathVariable("sessionId") Long sessionId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {

    Long userId = userDetails.getId();
    InterviewSettingDetailResponse response =
        preparationService.getSettingBySessionId(sessionId, userId);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/tips")
  public ResponseEntity<ApiResponse<AnswerTipResponse>> generateAnswerTip(
      @RequestBody AnswerTipRequest request) {

    AnswerTipResponse response = preparationService.generateAnswerTip(request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
