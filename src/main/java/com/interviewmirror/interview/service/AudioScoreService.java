package com.interviewmirror.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.realtime.dto.AudioSummaryDto;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AudioScoreService {

  private final ObjectMapper objectMapper;

  public AudioScoreService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Integer calculateQuestionScore(AudioSummaryDto summary) {
    if (summary == null) {
      return null;
    }

    log.debug(
        "[AudioScore] wpm={} rmsCoV={} speechRatio={} pauseCount={} avgPauseDurationMs={}"
            + " fillerWordCount={} wordCount={} ttr={} responseLatencyMs={} endFadeOut={}",
        summary.getWpm(),
        summary.getRmsCoV(),
        summary.getSpeechRatio(),
        summary.getPauseCount(),
        summary.getAvgPauseDurationMs(),
        summary.getFillerWordCount(),
        summary.getWordCount(),
        summary.getTtr(),
        summary.getResponseLatencyMs(),
        summary.getEndFadeOut());

    List<Integer> scores =
        Arrays.asList(
                scoreSpeechRate(summary.getWpm()),
                scoreVolumeStability(summary.getRmsCoV()),
                scoreSpeechRatio(summary.getSpeechRatio()),
                scorePause(summary.getPauseCount(), summary.getAvgPauseDurationMs()),
                scoreFillerRatio(summary.getFillerWordCount(), summary.getWordCount()),
                scoreVocabulary(summary.getTtr()),
                scoreLatency(summary.getResponseLatencyMs()),
                scoreEnding(summary.getEndFadeOut()))
            .stream()
            .filter(Objects::nonNull)
            .toList();

    if (scores.isEmpty()) {
      return null;
    }

    return (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
  }

  public String serializeSummary(AudioSummaryDto summary) {
    if (summary == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(summary);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("audioSummary serialization failed", e);
    }
  }

  public Integer calculateSessionScore(List<InterviewDetail> details) {
    if (details == null || details.isEmpty()) {
      return null;
    }

    List<Integer> scores =
        details.stream().map(InterviewDetail::getAudioScore).filter(Objects::nonNull).toList();
    if (scores.isEmpty()) {
      return null;
    }

    return (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
  }

  public String mergeAudioAnalysis(
      String aiAnalysisJson, Integer audioScore, int scoredQuestionCount) {
    if (audioScore == null) {
      return aiAnalysisJson;
    }

    ObjectNode root = parseObjectOrEmpty(aiAnalysisJson);
    ObjectNode audioNode = objectMapper.createObjectNode();
    audioNode.put("overallScore", audioScore);
    audioNode.put("scoredQuestionCount", scoredQuestionCount);
    root.set("audio", audioNode);
    return root.toString();
  }

  private ObjectNode parseObjectOrEmpty(String json) {
    if (json == null || json.isBlank()) {
      return objectMapper.createObjectNode();
    }
    try {
      if (objectMapper.readTree(json) instanceof ObjectNode objectNode) {
        return objectNode;
      }
    } catch (JsonProcessingException ignored) {
      // Fall through to preserving the original value in a wrapper.
    }

    ObjectNode root = objectMapper.createObjectNode();
    root.put("originalAnalysis", json);
    return root;
  }

  private Integer scoreSpeechRate(Integer wpm) {
    if (wpm == null) {
      return null;
    }
    if (wpm >= 130 && wpm <= 180) {
      return 100;
    }
    return clampScore(100 * (1 - Math.abs(wpm - 155) / 55.0));
  }

  private Integer scoreVolumeStability(Double rmsCoV) {
    if (rmsCoV == null) {
      return null;
    }
    if (rmsCoV < 0.2) {
      return 100;
    }
    return clampScore(100 * Math.max(0, 1 - rmsCoV / 0.5));
  }

  private Integer scoreSpeechRatio(Double speechRatio) {
    if (speechRatio == null) {
      return null;
    }
    double distance = Math.abs(speechRatio - 0.7);
    return clampScore(100 * Math.max(0, 1 - distance / 0.7));
  }

  private Integer scorePause(Integer pauseCount, Integer avgPauseDurationMs) {
    if (pauseCount == null && avgPauseDurationMs == null) {
      return null;
    }
    if (pauseCount != null
        && pauseCount > 0
        && avgPauseDurationMs != null
        && avgPauseDurationMs < 3000) {
      return 100;
    }
    if (avgPauseDurationMs != null && avgPauseDurationMs >= 5000) {
      return 50;
    }
    return 80;
  }

  private Integer scoreFillerRatio(Integer fillerWordCount, Integer wordCount) {
    if (fillerWordCount == null || wordCount == null || wordCount <= 0) {
      return null;
    }
    return clampScore(100 * Math.max(0, 1 - (fillerWordCount / (double) wordCount) * 10));
  }

  private Integer scoreVocabulary(Double ttr) {
    if (ttr == null) {
      return null;
    }
    return clampScore(100 * Math.min(1, ttr / 0.7));
  }

  private Integer scoreLatency(Integer responseLatencyMs) {
    if (responseLatencyMs == null) {
      return null;
    }
    if (responseLatencyMs < 2000) {
      return 100;
    }
    return clampScore(100 - (responseLatencyMs - 2000) / 100.0);
  }

  private Integer scoreEnding(Boolean endFadeOut) {
    if (endFadeOut == null) {
      return null;
    }
    return endFadeOut ? 60 : 100;
  }

  private Integer clampScore(double value) {
    return (int) Math.round(Math.max(0, Math.min(100, value)));
  }
}
