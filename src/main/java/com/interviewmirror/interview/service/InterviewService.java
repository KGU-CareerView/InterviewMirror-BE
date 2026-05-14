package com.interviewmirror.interview.service;

import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.interview.dto.AiReportResponse;
import com.interviewmirror.interview.dto.InterviewReportResponse;
import com.interviewmirror.interview.dto.InterviewResultResponse;
import com.interviewmirror.interview.entity.InterviewReport;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.repository.InterviewReportRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import java.util.List;
import java.util.stream.Collectors;
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
                        .build())
            .toList(); // Java 16 이상이면 toList() 사용, 미만이면 collect(Collectors.toList())

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

  @Transactional
  public void saveInterviewReport(AiReportResponse response) {
    if (reportRepository.existsById(response.getSessionId())) {
      log.info("[RabbitMQ] 이미 저장된 리포트. 무시합니다. SessionID: {}", response.getSessionId());
      return;
    }

    // 예외를 던지지 않고 Optional로 받아서 부드럽게 처리
    resultRepository
        .findById(response.getSessionId())
        .ifPresentOrElse(
            result -> {
              InterviewReport report =
                  InterviewReport.builder()
                      .interviewResult(result)
                      .totalScore(response.getTotalScore())
                      .feedback(response.getFeedback())
                      .strengths(response.getStrengths())
                      .weaknesses(response.getWeaknesses())
                      .aiAnalysisJson(response.getAiAnalysisJson())
                      .build();
              reportRepository.save(report);
            },
            () -> {
              // 세션이 삭제된 경우: 에러를 던지지 않고 로그만 남기고 정상 종료 처리(ACK)
              log.warn(
                  "[RabbitMQ] 리포트 수신했으나 해당 세션을 찾을 수 없습니다 (삭제됨). SessionID: {}",
                  response.getSessionId());
            });
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
