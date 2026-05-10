package com.interviewmirror.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

// 답변 팁 생성 요청 DTO

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AnswerTipRequest {
    @NotBlank(message = "질문 내용은 필수입니다.")
    private String question;

    private String resumeContent;
}