package com.interviewmirror.domain.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackEndRequest {

    @NotBlank(message = "sessionId cannot be blank")
    private String sessionId;
}
