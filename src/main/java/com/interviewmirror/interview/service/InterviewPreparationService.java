package com.interviewmirror.interview.service;

import com.interviewmirror.infrastructure.AiGrpcClient;
import com.interviewmirror.interview.dto.AnswerTipRequest;
import com.interviewmirror.interview.dto.AnswerTipResponse;
import com.interviewmirror.interview.dto.InterviewSettingDetailResponse;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSetting;
import com.interviewmirror.interview.repository.InterviewSettingRepository;
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
  private final SessionService sessionService;

  @Transactional
  public Long saveSetting(Long sessionId, Long userId, InterviewSettingRequest request) {
    InterviewResult session = sessionService.getValidatedSession(sessionId, userId);

    InterviewSetting setting =
        InterviewSetting.builder()
            .interviewResult(session)
            .category(request.getCategory())
            .interviewType(request.getInterviewType())
            .difficulty(request.getDifficulty())
            .questionCount(request.getQuestionCount())
            .timePerQuestion(request.getTimePerQuestion())
            .resumeContent(request.getResumeContent())
            .build();

    InterviewSetting savedSetting = settingRepository.save(setting);
    log.info("[SessionID: {}] 면접 사전 설정 완료. Setting ID: {}", sessionId, savedSetting.getSettingId());

    aiGrpcClient.requestInitialQuestions(
        request.getCategory(),
        request.getInterviewType(),
        request.getDifficulty(),
        request.getQuestionCount(),
        request.getResumeContent());

    return savedSetting.getSettingId();
  }

  @Transactional(readOnly = true)
  public InterviewSettingDetailResponse getSettingBySessionId(Long sessionId, Long userId) {
    // 1. 소유권 및 세션 존재 검증
    sessionService.getValidatedSession(sessionId, userId);

    // 2. 설정 정보 조회
    InterviewSetting setting =
        settingRepository
            .findByInterviewResult_SessionId(sessionId)
            .orElseThrow(
                () -> new EntityNotFoundException("해당 세션의 설정 정보가 없습니다. SessionID: " + sessionId));

    // 3. 엔티티를 DTO로 수동 매핑하여 반환 (500 에러 무한루프 방지)
    return InterviewSettingDetailResponse.builder()
        .settingId(setting.getSettingId())
        .category(setting.getCategory())
        .interviewType(setting.getInterviewType())
        .difficulty(setting.getDifficulty())
        .questionCount(setting.getQuestionCount())
        .timePerQuestion(setting.getTimePerQuestion())
        .resumeContent(setting.getResumeContent())
        .build();
  }

  public AnswerTipResponse generateAnswerTip(AnswerTipRequest request) {
    String generatedTip =
        aiGrpcClient.requestTipGeneration(request.getQuestion(), request.getResumeContent());
    return AnswerTipResponse.builder().tip(generatedTip).build();
  }
}
