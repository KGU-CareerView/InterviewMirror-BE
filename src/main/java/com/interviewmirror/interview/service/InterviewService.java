package com.interviewmirror.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.grpc.proto.FinalReportResponse;
import com.interviewmirror.grpc.proto.QuestionFeedback;
import com.interviewmirror.interview.dto.InterviewReportResponse;
import com.interviewmirror.interview.dto.InterviewResultResponse;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewReport;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.ReportStatus;
import com.interviewmirror.interview.repository.InterviewReportRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 클래스 전체에 읽기 전용 트랜잭션 적용
public class InterviewService {

  private final InterviewResultRepository resultRepository;
  private final SessionService sessionService; // 1. 의존성 주입 추가
  private final AudioScoreService audioScoreService;

  // 특정 세션의 면접 결과(감정 분석 데이터)를 조회합니다.
  @Transactional(readOnly = true)
  public InterviewResultResponse getInterviewResult(Long sessionId, Long userId) {
    // 세션 존재 확인 + 소유권 검증 후 엔티티 반환
    InterviewResult result = sessionService.getValidatedSession(sessionId, userId);

    // 자식 엔티티(InterviewDetail) 리스트를 DTO 리스트로 변환
    List<InterviewResultResponse.DetailDto> detailDtos =
        result.getDetails().stream()
            .map(
                detail ->
                    InterviewResultResponse.DetailDto.builder()
                        .qId(detail.getQId())
                        .question(detail.getQuestion())
                        .answer(detail.getAnswer())
                        .emotionResult(detail.getEmotionResult())
                        .responseTimeSeconds(detail.getResponseTimeSeconds())
                        .totalScore(detail.getTotalScore())
                        .contentScore(detail.getContentScore())
                        .voiceScore(detail.getAudioScore())
                        .expressionScore(detail.getExpressionScore())
                        .feedback(detail.getFeedback())
                        .contentFeedback(detail.getContentFeedback())
                        .voiceFeedback(detail.getVoiceFeedback())
                        .expressionFeedback(detail.getExpressionFeedback())
                        .build())
            .toList();

    // 부모 데이터와 자식 데이터를 합쳐서 최종 DTO 생성 및 반환
    return InterviewResultResponse.builder()
        .sessionId(result.getSessionId())
        .videoUrl(result.getVideoUrl())
        .createTime(result.getCreateTime())
        // 그래프 데이터가 없으면 "PROCESSING" 상태로 반환
        .emotionGraph(
            (result.getEmotionGraph() != null && !result.getEmotionGraph().isEmpty())
                ? result.getEmotionGraph()
                : "PROCESSING")
        .details(detailDtos)
        .build();
  }

  // 로그인한 사용자의 모든 면접 기록(세션 ID 리스트)을 조회합니다.
  public List<Long> getHistory(Long userId) {
    // DB에서 해당 유저의 모든 면접 결과 리스트 조회
    List<InterviewResult> results = resultRepository.findByUserId(userId);

    // Entity 객체 리스트에서 sessionId만 추출하여 반환
    return results.stream().map(InterviewResult::getSessionId).collect(Collectors.toList());
  }

  private final InterviewReportRepository reportRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public void saveReportFromGrpc(Long sessionId, FinalReportResponse response) {
    if (reportRepository.existsById(sessionId)) {
      log.info("[gRPC] 이미 저장된 리포트. 무시합니다. SessionID: {}", sessionId);
      return;
    }

    resultRepository
        .findById(sessionId)
        .ifPresentOrElse(
            result -> {
              applyQuestionFeedbacks(result, response.getQuestionFeedbacksList());

              Integer audioScore = audioScoreService.calculateSessionScore(result.getDetails());
              int scoredQuestionCount =
                  (int)
                      result.getDetails().stream()
                          .filter(detail -> detail.getAudioScore() != null)
                          .count();

              String aiAnalysisJson = toJson(response);
              String mergedAnalysisJson =
                  audioScoreService.mergeAudioAnalysis(
                      aiAnalysisJson, audioScore, scoredQuestionCount);

              InterviewReport report =
                  InterviewReport.builder()
                      .interviewResult(result)
                      .totalScore((int) Math.round(response.getOverallScore()))
                      .feedback(response.getFinalAdvice())
                      .strengths(serializeList(response.getStrengthsList()))
                      .weaknesses(serializeList(response.getWeaknessesList()))
                      .aiAnalysisJson(mergedAnalysisJson)
                      .build();
              reportRepository.save(report);
              result.setReportStatus(ReportStatus.COMPLETED);
              log.info(
                  "[gRPC] 리포트 저장 완료. SessionID: {} score={}", sessionId, report.getTotalScore());
            },
            () -> log.warn("[gRPC] 리포트 수신했으나 세션을 찾을 수 없습니다 (삭제됨). SessionID: {}", sessionId));
  }

  // gRPC onError 콜백에서 호출 — 재시도 가능 여부에 따라 PENDING/FAILED 마킹
  @Transactional
  public void markReportStatus(Long sessionId, ReportStatus status) {
    resultRepository
        .findById(sessionId)
        .ifPresent(
            result -> {
              result.setReportStatus(status);
              log.info("[gRPC] 리포트 상태 갱신: SessionID={} status={}", sessionId, status);
            });
  }

  // 질문별 피드백을 인덱스 매칭하여 InterviewDetail에 반영 (JPA dirty checking으로 자동 저장)
  private void applyQuestionFeedbacks(InterviewResult result, List<QuestionFeedback> feedbacks) {
    if (feedbacks == null || feedbacks.isEmpty()) {
      return;
    }

    List<InterviewDetail> details = result.getDetails();
    Map<Integer, InterviewDetail> detailByIndex =
        IntStream.range(0, details.size())
            .boxed()
            .collect(Collectors.toMap(i -> i + 1, details::get, (a, b) -> a));

    for (QuestionFeedback feedback : feedbacks) {
      InterviewDetail detail = detailByIndex.get(feedback.getIndex());
      if (detail == null) {
        log.warn(
            "[gRPC] 질문 인덱스에 해당하는 InterviewDetail 없음 sessionId={} index={}",
            result.getSessionId(),
            feedback.getIndex());
        continue;
      }
      detail.setTotalScore((int) Math.round(feedback.getTotalScore()));
      detail.setContentScore((int) Math.round(feedback.getContentScore()));
      detail.setExpressionScore((int) Math.round(feedback.getExpressionScore()));
      detail.setAudioScore((int) Math.round(feedback.getVoiceScore()));
      detail.setFeedback(feedback.getOverallFeedback());
      detail.setContentFeedback(feedback.getContentFeedback());
      detail.setVoiceFeedback(feedback.getVoiceFeedback());
      detail.setExpressionFeedback(feedback.getExpressionFeedback());
    }
  }

  private String toJson(FinalReportResponse response) {
    try {
      return JsonFormat.printer().includingDefaultValueFields().print(response);
    } catch (InvalidProtocolBufferException e) {
      log.warn("[gRPC] FinalReportResponse 직렬화 실패: {}", e.getMessage());
      return "{}";
    }
  }

  private String serializeList(List<?> list) {
    try {
      return objectMapper.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }

  @Transactional(readOnly = true)
  public InterviewReportResponse getInterviewReport(Long sessionId, Long userId) {
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

    // 세션의 주인과 현재 요청한 유저가 다르면 예외 발생
    if (!result.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED); // 또는 접근 권한 에러코드
    }

    if (result.getReportStatus() == ReportStatus.FAILED) {
      throw new BusinessException(ErrorCode.REPORT_GENERATION_FAILED);
    }

    InterviewReport report =
        reportRepository
            .findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_READY));

    return InterviewReportResponse.builder()
        .sessionId(result.getSessionId())
        .videoUrl(result.getVideoUrl())
        .emotionGraphJson(result.getEmotionGraph()) // InterviewResult에 있는 감정 데이터
        .totalScore(report.getTotalScore())
        .feedback(report.getFeedback())
        .strengths(report.getStrengths())
        .weaknesses(report.getWeaknesses())
        .aiAnalysisJson(report.getAiAnalysisJson()) // InterviewReport에 있는 심층 분석 데이터
        .build();
  }
}
