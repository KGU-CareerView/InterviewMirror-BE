package com.interviewmirror.domain.interview.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSettingRequest {
    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @NotBlank(message = "분야(Category)를 선택해주세요.")
    private String category;

    @NotBlank(message = "면접 유형을 선택해주세요.")
    private String interviewType;

    @NotBlank(message = "난이도를 선택해주세요.")
    private String difficulty;

    @NotNull(message = "질문 개수를 설정해주세요.")
    @Min(value = 1, message = "질문 개수는 최소 1개 이상이어야 합니다.")
    private Integer questionCount;

    @NotNull(message = "질문당 시간을 설정해주세요.")
    @Min(value = 30, message = "질문당 시간은 최소 30초 이상이어야 합니다.")
    private Integer timePerQuestion;

    private String resumeContent; // 자소서가 없는 경우도 있으므로 Null 허용
}