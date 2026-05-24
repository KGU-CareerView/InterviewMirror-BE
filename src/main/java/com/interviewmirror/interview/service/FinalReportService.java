package com.interviewmirror.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmirror.grpc.proto.AudioSummaryData;
import com.interviewmirror.grpc.proto.FinalReportRequest;
import com.interviewmirror.grpc.proto.FinalReportResponse;
import com.interviewmirror.grpc.proto.QuestionAnalysisResult;
import com.interviewmirror.grpc.proto.VoiceToneAnalysisRequest;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.entity.InterviewSetting;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import com.interviewmirror.interview.repository.InterviewSettingRepository;
import com.interviewmirror.realtime.client.AiGrpcClient;
import com.interviewmirror.realtime.dto.AudioSummaryDto;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
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

    analyzeVoiceToneForAllQuestions(result, details);

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
            log.error("[gRPC] 최종 리포트 생성 실패 sessionId={}", sessionId, t);
          }

          @Override
          public void onCompleted() {
            log.info("[gRPC] 최종 리포트 생성 완료 sessionId={}", sessionId);
          }
        });
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
