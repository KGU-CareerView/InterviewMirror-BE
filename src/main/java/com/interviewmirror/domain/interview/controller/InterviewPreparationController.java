package com.interviewmirror.domain.interview.controller;

import com.interviewmirror.domain.interview.dto.AnswerTipRequest;
import com.interviewmirror.domain.interview.dto.AnswerTipResponse;
import com.interviewmirror.domain.interview.dto.InterviewSettingRequest;
import com.interviewmirror.domain.interview.dto.InterviewSettingResponse;
import com.interviewmirror.domain.interview.entity.InterviewSetting;
import com.interviewmirror.domain.interview.service.InterviewPreparationService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/preparation") // 경로명은 상황에 맞게 변경 가능합니다.
public class InterviewPreparationController {

    private final InterviewPreparationService preparationService;

    @PostMapping("/settings")
    public ResponseEntity<InterviewSettingResponse> saveInterviewSetting(
            @Valid @RequestBody InterviewSettingRequest request) {

        Long settingId = preparationService.saveSetting(request);

        InterviewSettingResponse response = InterviewSettingResponse.builder()
                .settingId(settingId)
                .message("면접 설정이 성공적으로 저장되었습니다.")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/settings/user/{userId}")
    public ResponseEntity<InterviewSetting> getLatestSetting(@PathVariable Long userId) {
        InterviewSetting setting = preparationService.getLatestSetting(userId);
        return ResponseEntity.ok(setting);
    }

    @PostMapping("/tips")
    public ResponseEntity<AnswerTipResponse> generateAnswerTip(
            @RequestBody AnswerTipRequest request) {

        AnswerTipResponse response = preparationService.generateAnswerTip(request);

        return ResponseEntity.ok(response);
    }
}