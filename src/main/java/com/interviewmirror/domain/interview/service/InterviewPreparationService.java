package com.interviewmirror.domain.interview.service;

import com.interviewmirror.domain.interview.dto.AnswerTipRequest;
import com.interviewmirror.domain.interview.dto.AnswerTipResponse;
import com.interviewmirror.domain.interview.dto.InterviewSettingRequest;
import com.interviewmirror.domain.interview.entity.InterviewSetting;
import com.interviewmirror.domain.interview.repository.InterviewSettingRepository;
import com.interviewmirror.config.AiGrpcClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPreparationService {

    private final InterviewSettingRepository settingRepository;
    private final AiGrpcClient aiGrpcClient;


    @Transactional
    public Long saveSetting(InterviewSettingRequest request) {
        InterviewSetting setting = InterviewSetting.builder()
                .userId(request.getUserId())
                .category(request.getCategory())
                .interviewType(request.getInterviewType())
                .difficulty(request.getDifficulty())
                .questionCount(request.getQuestionCount())
                .timePerQuestion(request.getTimePerQuestion())
                .resumeContent(request.getResumeContent())
                .build();

        InterviewSetting savedSetting = settingRepository.save(setting);
        log.info("면접 사전 설정 완료. Setting ID: {}", savedSetting.getSettingId());

        aiGrpcClient.requestInitialQuestions(
                request.getCategory(),
                request.getInterviewType(),
                request.getDifficulty(),
                request.getQuestionCount(),
                request.getResumeContent()
        );
        return savedSetting.getSettingId();
    }

    @Transactional(readOnly = true)
    public InterviewSetting getLatestSetting(Long userId) {
        return settingRepository.findTopByUserIdOrderBySettingIdDesc(userId)
                .orElseThrow(() -> new EntityNotFoundException("해당 유저의 면접 설정 정보가 없습니다. UserID: " + userId));
    }

    public AnswerTipResponse generateAnswerTip(AnswerTipRequest request) {
        log.info("팁 생성 요청 질문: {}", request.getQuestion());

        String generatedTip = aiGrpcClient.requestTipGeneration(request.getQuestion(), request.getResumeContent());

        return AnswerTipResponse.builder()
                .tip(generatedTip)
                .build();
    }
}