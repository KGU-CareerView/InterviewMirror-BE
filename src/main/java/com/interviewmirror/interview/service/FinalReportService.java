package com.interviewmirror.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.exception.BusinessException;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.grpc.proto.AudioSummaryData;
import com.interviewmirror.grpc.proto.FinalReportRequest;
import com.interviewmirror.grpc.proto.FinalReportResponse;
import com.interviewmirror.grpc.proto.QuestionAnalysisResult;
import com.interviewmirror.grpc.proto.VoiceToneAnalysisRequest;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSetting;
import com.interviewmirror.interview.entity.ReportStatus;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import com.interviewmirror.interview.repository.InterviewSettingRepository;
import com.interviewmirror.realtime.client.AiGrpcClient;
import com.interviewmirror.realtime.dto.AudioSummaryDto;
import com.interviewmirror.realtime.dto.RealtimeResponse;
import com.interviewmirror.realtime.repository.RealtimeBufferRepository;
import com.interviewmirror.realtime.service.RealtimeFrameBuffer;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalReportService {

  private static final int VOICE_ANALYSIS_TIMEOUT_SECONDS = 10;

  private final InterviewResultRepository resultRepository;
  private final InterviewDetailRepository detailRepository;
  private final InterviewSettingRepository settingRepository;
  private final RedisSessionService redisSessionService;
  private final RealtimeBufferRepository realtimeBufferRepository;
  private final RealtimeFrameBuffer realtimeFrameBuffer;
  private final AiGrpcClient aiGrpcClient;
  private final InterviewService interviewService;
  private final ObjectMapper objectMapper;

  @Transactional
  public void requestFinalReport(Long sessionId) {
    InterviewResult result = resultRepository.findById(sessionId).orElse(null);
    if (result == null) {
      log.warn("[gRPC] 최종 리포트 요청 실패 - 세션 없음 sessionId={}", sessionId);
      return;
    }

    List<InterviewDetail> details = detailRepository.findByInterviewResult_SessionId(sessionId);
    InterviewSetting setting =
        settingRepository.findByInterviewResult_SessionId(sessionId).orElse(null);

    aggregateEmotionGraph(result);
    analyzeVoiceToneForAllQuestions(result, details);

    result.setReportStatus(ReportStatus.PENDING);

    FinalReportRequest request = buildRequest(result, setting, details);

    log.info("[gRPC] 최종 리포트 요청 sessionId={} questionCount={}", sessionId, details.size());

    aiGrpcClient.generateFinalReportAsync(
        request,
        new StreamObserver<FinalReportResponse>() {
          @Override
          public void onNext(FinalReportResponse response) {
            interviewService.saveReportFromGrpc(sessionId, response);
          }

          @Override
          public void onError(Throwable t) {
            boolean retryable = isRetryable(t);
            log.error("[gRPC] 최종 리포트 생성 실패 sessionId={} retryable={}", sessionId, retryable, t);
            interviewService.markReportStatus(
                sessionId, retryable ? ReportStatus.PENDING : ReportStatus.FAILED);
          }

          @Override
          public void onCompleted() {
            log.info("[gRPC] 최종 리포트 생성 완료 sessionId={}", sessionId);
          }
        });
  }

  // 리포트 생성 재시도 — PENDING/FAILED 상태에서만 허용
  @Transactional
  public void retryReportGeneration(Long sessionId, Long userId) {
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

    if (!result.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }

    if (result.getReportStatus() == ReportStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    log.info("[gRPC] 리포트 재시도 요청: SessionID={} 현재상태={}", sessionId, result.getReportStatus());
    requestFinalReport(sessionId);
  }

  // gRPC 표준 상태 코드 외에도, AI 서버가 INTERNAL로 래핑하면서 메시지에
  // RESOURCE_EXHAUSTED / 429를 남기는 경우(Gemini quota 등)도 재시도 가능으로 판정
  private boolean isRetryable(Throwable t) {
    if (t instanceof StatusRuntimeException sre) {
      Status.Code code = sre.getStatus().getCode();
      if (code == Status.Code.RESOURCE_EXHAUSTED
          || code == Status.Code.UNAVAILABLE
          || code == Status.Code.DEADLINE_EXCEEDED) {
        return true;
      }
      String description = sre.getStatus().getDescription();
      if (description != null
          && (description.contains("RESOURCE_EXHAUSTED") || description.contains("429"))) {
        return true;
      }
    }
    return false;
  }

  private void aggregateEmotionGraph(InterviewResult result) {
    String sessionId = String.valueOf(result.getSessionId());
    // realtime.end 이후 도착한 프레임도 포함하기 위해 마지막으로 한 번 더 flush
    realtimeFrameBuffer.flushSession(sessionId);
    List<RealtimeResponse> frames = realtimeBufferRepository.findAll(sessionId);
    if (frames.isEmpty()) {
      log.warn("[gRPC] emotionGraph 생성 - 실시간 프레임 없음 sessionId={}", sessionId);
      return;
    }

    List<Map<String, Object>> timeline = new ArrayList<>(frames.size());
    for (RealtimeResponse frame : frames) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("timestamp", frame.getTimestamp());
      point.put("label", frame.getLabel());
      point.put("confidence", frame.getConfidence());
      point.put("faceDetected", frame.isFaceDetected());
      timeline.add(point);
    }

    try {
      String emotionGraphJson = objectMapper.writeValueAsString(timeline);
      result.setEmotionGraph(emotionGraphJson);
      log.info("[gRPC] emotionGraph 집계 완료 sessionId={} frameCount={}", sessionId, frames.size());
    } catch (Exception e) {
      log.warn("[gRPC] emotionGraph 직렬화 실패 sessionId={}: {}", sessionId, e.getMessage());
    }
  }

  private void analyzeVoiceToneForAllQuestions(
      InterviewResult result, List<InterviewDetail> details) {
    record VoiceTask(InterviewDetail detail, int questionIndex, VoiceToneAnalysisRequest request) {}

    List<VoiceTask> tasks = new ArrayList<>();
    for (int i = 0; i < details.size(); i++) {
      InterviewDetail detail = details.get(i);
      int questionIndex = i + 1;
      AudioSummaryData audioSummary = parseAudioSummary(detail.getAudioSummaryJson());
      if (audioSummary == null) continue;

      List<Float> zcrSamples =
          redisSessionService.getZcrSamples(result.getSessionId(), questionIndex);

      tasks.add(
          new VoiceTask(
              detail,
              questionIndex,
              VoiceToneAnalysisRequest.newBuilder()
                  .setSessionId(String.valueOf(result.getSessionId()))
                  .setUserId(String.valueOf(result.getUserId()))
                  .setQuestionIndex(questionIndex)
                  .setResponseTimeSeconds(
                      detail.getResponseTimeSeconds() != null ? detail.getResponseTimeSeconds() : 0)
                  .setAudioSummary(audioSummary)
                  .addAllZcrSamples(zcrSamples)
                  .build()));
    }

    // async stub으로 모든 요청을 동시에 발송, CompletableFuture로 결과 수집
    List<CompletableFuture<Integer>> futures =
        tasks.stream()
            .map(
                task ->
                    aiGrpcClient
                        .analyzeVoiceToneAsync(task.request())
                        .thenApply(r -> (int) Math.round(r.getOverallStabilityScore()))
                        .exceptionally(
                            e -> {
                              log.warn(
                                  "[gRPC] VoiceTone 분석 실패 sessionId={} questionIndex={} error={}",
                                  result.getSessionId(),
                                  task.questionIndex(),
                                  e.getMessage());
                              return null;
                            }))
            .toList();

    // 메인 스레드에서 결과 수집 후 DB 저장 (JPA 세션 유지)
    for (int i = 0; i < tasks.size(); i++) {
      try {
        Integer aiScore = futures.get(i).get(VOICE_ANALYSIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (aiScore != null) {
          VoiceTask task = tasks.get(i);
          task.detail().setAudioScore(aiScore);
          detailRepository.save(task.detail());
          log.info(
              "[gRPC] VoiceTone 분석 완료 sessionId={} questionIndex={} score={}",
              result.getSessionId(),
              task.questionIndex(),
              aiScore);
        }
      } catch (Exception e) {
        log.warn("[gRPC] VoiceTone 결과 수집 실패 sessionId={} index={}", result.getSessionId(), i + 1);
      }
    }
  }

  private FinalReportRequest buildRequest(
      InterviewResult result, InterviewSetting setting, List<InterviewDetail> details) {
    FinalReportRequest.Builder builder =
        FinalReportRequest.newBuilder()
            .setSessionId(String.valueOf(result.getSessionId()))
            .setUserId(String.valueOf(result.getUserId()))
            .setLanguage("ko")
            .setEmotionGraphJson(orEmpty(result.getEmotionGraph()));

    if (setting != null) {
      builder
          .setCategory(orEmpty(setting.getCategory()))
          .setInterviewType(orEmpty(setting.getInterviewType()))
          .setDifficulty(orEmpty(setting.getDifficulty()))
          .setResumeText(orEmpty(setting.getResumeContent()));
    }

    for (int i = 0; i < details.size(); i++) {
      InterviewDetail detail = details.get(i);
      int questionIndex = i + 1;

      List<Float> zcrSamples =
          redisSessionService.getZcrSamples(result.getSessionId(), questionIndex);

      String answer = orEmpty(detail.getAnswer());

      QuestionAnalysisResult.Builder qaBuilder =
          QuestionAnalysisResult.newBuilder()
              .setIndex(questionIndex)
              .setQuestion(orEmpty(detail.getQuestion()))
              .setAnswer(answer)
              .setAnswerLength(answer.length())
              .setResponseTimeSeconds(
                  detail.getResponseTimeSeconds() != null ? detail.getResponseTimeSeconds() : 0)
              .setEmotionResultJson(orEmpty(detail.getEmotionResult()))
              .setVoiceScore(detail.getAudioScore() != null ? detail.getAudioScore() : 0.0)
              .addAllZcrSamples(zcrSamples);

      AudioSummaryData audioSummary = parseAudioSummary(detail.getAudioSummaryJson());
      if (audioSummary != null) {
        qaBuilder.setAudioSummary(audioSummary);
      }

      builder.addQuestionResults(qaBuilder.build());
    }

    return builder.build();
  }

  private AudioSummaryData parseAudioSummary(String json) {
    if (json == null || json.isBlank()) return null;
    try {
      AudioSummaryDto dto = objectMapper.readValue(json, AudioSummaryDto.class);
      AudioSummaryData.Builder b = AudioSummaryData.newBuilder();
      if (dto.getSpeechRatio() != null) b.setSpeechRatio(dto.getSpeechRatio().floatValue());
      if (dto.getAvgRms() != null) b.setAvgRms(dto.getAvgRms().floatValue());
      if (dto.getRmsCoV() != null) b.setRmsCov(dto.getRmsCoV().floatValue());
      if (dto.getWpm() != null) b.setWpm(dto.getWpm());
      if (dto.getPauseCount() != null) b.setPauseCount(dto.getPauseCount());
      if (dto.getAvgPauseDurationMs() != null) b.setAvgPauseDurationMs(dto.getAvgPauseDurationMs());
      if (dto.getMaxPauseDurationMs() != null) b.setMaxPauseDurationMs(dto.getMaxPauseDurationMs());
      if (dto.getResponseLatencyMs() != null) b.setResponseLatencyMs(dto.getResponseLatencyMs());
      if (dto.getEndFadeOut() != null) b.setEndFadeOut(dto.getEndFadeOut());
      if (dto.getEstimatedFillerCount() != null)
        b.setEstimatedFillerCount(dto.getEstimatedFillerCount());
      if (dto.getFillerWordCount() != null) b.setFillerWordCount(dto.getFillerWordCount());
      if (dto.getWordCount() != null) b.setWordCount(dto.getWordCount());
      if (dto.getTtr() != null) b.setTtr(dto.getTtr().floatValue());
      return b.build();
    } catch (Exception e) {
      log.warn("[gRPC] audioSummaryJson 파싱 실패: {}", e.getMessage());
      return null;
    }
  }

  private String orEmpty(String value) {
    return value != null ? value : "";
  }
}
