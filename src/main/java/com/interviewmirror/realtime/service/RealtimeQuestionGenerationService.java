package com.interviewmirror.realtime.service;

import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.grpc.proto.InitialQuestionGenerateResponse;
import com.interviewmirror.grpc.proto.QuestionItem;
import com.interviewmirror.interview.dto.InterviewSettingRequest;
import com.interviewmirror.interview.entity.InterviewSessionState;
import com.interviewmirror.interview.service.RedisSessionService;
import com.interviewmirror.interview.service.SessionStateService;
import com.interviewmirror.realtime.client.AiGrpcClient;
import com.interviewmirror.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeQuestionGenerationService {

  private static final String DEMO_USER_EMAIL = "xogh080907@gmail.com";
  private static final String DEMO_CATEGORY = "DESIGN";
  private static final int DEMO_QUESTION_COUNT = 5;
  private static final long DEMO_PUBLISH_DELAY_MS = 1500L;
  private static final List<String> DEMO_DESIGN_QUESTIONS =
      List.of(
          "지원자님께서 가장 중요하게 생각하시는 디자인 철학이나 원칙은 무엇인가요?",
          "말씀해주신 디자인 철학을 실제 프로젝트에 적용하여 구체적인 성과를 냈던 사례가 있다면 공유해주실 수 있을까요?",
          "개발자나 기획자와 의견 충돌이 발생했을 때, 어떻게 조율하고 협업하시나요?",
          "최근 가장 인상 깊게 본 디자인 서비스는 무엇이며, 그 이유는 무엇인가요?",
          "디자인 업무를 수행하면서 가장 어려웠던 점은 무엇이며, 이를 어떻게 극복하셨나요?");

  private final AiGrpcClient aiGrpcClient;
  private final RedisSessionService redisSessionService;
  private final SessionStateService sessionStateService;
  private final RealtimeMessagePublisher realtimeMessagePublisher;
  private final UserRepository userRepository;

  @Async("aiTaskExecutor")
  public void generateInitialQuestions(
      Long sessionId, Long userId, InterviewSettingRequest request) {
    log.info(
        "[SessionID: {}] 초기 질문 생성 진입 - userId={}, category='{}', interviewType='{}', difficulty='{}', questionCount={}",
        sessionId,
        userId,
        request.getCategory(),
        request.getInterviewType(),
        request.getDifficulty(),
        request.getQuestionCount());

    if (!redisSessionService.lockQuestionGeneration(sessionId)) {
      log.warn("[SessionID: {}] 초기 질문 생성 중복 요청 감지. 이미 처리 중입니다.", sessionId);
      realtimeMessagePublisher.publishQuestionProcessingWarning(sessionId);
      return;
    }

    try {
      List<QuestionItem> demoQuestions = tryBuildDemoQuestions(sessionId, userId, request);
      if (demoQuestions != null) {
        log.info("[SessionID: {}] 데모 사용자 감지 - AI 호출 없이 사전 정의된 디자인 질문 사용", sessionId);
        sleepForDemoPacing(sessionId);
        publishQuestions(sessionId, demoQuestions);
        return;
      }

      InitialQuestionGenerateResponse response =
          aiGrpcClient.requestInitialQuestions(
              sessionId,
              userId,
              request.getCategory(),
              request.getInterviewType(),
              request.getDifficulty(),
              request.getQuestionCount(),
              request.getTimePerQuestion(),
              request.getResumeContent());

      List<QuestionItem> questions = response.getQuestionsList();
      if (questions.isEmpty()) {
        log.warn("[SessionID: {}] AI 서버가 빈 초기 질문 목록을 응답했습니다.", sessionId);
        realtimeMessagePublisher.publishSessionError(sessionId, ErrorCode.AI_RESPONSE_FAILED);
        return;
      }

      publishQuestions(sessionId, questions);
    } catch (Exception e) {
      log.error("[SessionID: {}] 초기 질문 생성 실패", sessionId, e);
      realtimeMessagePublisher.publishSessionError(sessionId, ErrorCode.AI_RESPONSE_FAILED);
    } finally {
      redisSessionService.unlockQuestionGeneration(sessionId);
    }
  }

  private void publishQuestions(Long sessionId, List<QuestionItem> questions) {
    String firstQuestion = questions.get(0).getQuestion();
    redisSessionService.setLastQuestion(sessionId, firstQuestion);
    sessionStateService.changeStateBySystem(sessionId, InterviewSessionState.IN_PROGRESS);
    realtimeMessagePublisher.publishInitialQuestionsReady(
        sessionId, firstQuestion, questions.stream().map(this::toPayload).toList());
  }

  private List<QuestionItem> tryBuildDemoQuestions(
      Long sessionId, Long userId, InterviewSettingRequest request) {
    boolean categoryMatch = DEMO_CATEGORY.equals(request.getCategory());
    boolean countMatch =
        request.getQuestionCount() != null && request.getQuestionCount() == DEMO_QUESTION_COUNT;
    log.info(
        "[SessionID: {}] 데모 케이스 체크 - categoryMatch={} (expected='{}', actual='{}'), countMatch={} (expected={}, actual={})",
        sessionId,
        categoryMatch,
        DEMO_CATEGORY,
        request.getCategory(),
        countMatch,
        DEMO_QUESTION_COUNT,
        request.getQuestionCount());

    if (!categoryMatch || !countMatch) {
      return null;
    }

    String actualEmail = userRepository.findById(userId).map(user -> user.getEmail()).orElse(null);
    boolean emailMatch = DEMO_USER_EMAIL.equalsIgnoreCase(actualEmail);
    log.info(
        "[SessionID: {}] 데모 케이스 이메일 체크 - emailMatch={} (expected='{}', actual='{}')",
        sessionId,
        emailMatch,
        DEMO_USER_EMAIL,
        actualEmail);

    return emailMatch ? buildDemoDesignQuestions() : null;
  }

  private void sleepForDemoPacing(Long sessionId) {
    try {
      Thread.sleep(DEMO_PUBLISH_DELAY_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("[SessionID: {}] 데모 페이싱 슬립 중 인터럽트 발생", sessionId);
    }
  }

  private List<QuestionItem> buildDemoDesignQuestions() {
    return IntStream.range(0, DEMO_DESIGN_QUESTIONS.size())
        .mapToObj(
            i ->
                QuestionItem.newBuilder()
                    .setIndex(i + 1)
                    .setQuestion(DEMO_DESIGN_QUESTIONS.get(i))
                    .setTooltip("")
                    .setCategory(DEMO_CATEGORY)
                    .setIntent("")
                    .build())
        .toList();
  }

  @Async("aiTaskExecutor")
  public void generateFollowUpQuestion(Long sessionId, String previousQuestion, String answer) {
    if (!redisSessionService.lockQuestionGeneration(sessionId)) {
      log.warn("[SessionID: {}] AI 질문 생성 중복 요청 감지. 이미 처리 중입니다.", sessionId);
      realtimeMessagePublisher.publishQuestionProcessingWarning(sessionId);
      return;
    }

    try {
      int demoIndex = DEMO_DESIGN_QUESTIONS.indexOf(previousQuestion);
      if (demoIndex >= 0) {
        int nextIndex = demoIndex + 1;
        if (nextIndex < DEMO_DESIGN_QUESTIONS.size()) {
          String demoNext = DEMO_DESIGN_QUESTIONS.get(nextIndex);
          log.info(
              "[SessionID: {}] 데모 케이스 - 사전 정의 질문 진행 ({}/{})",
              sessionId,
              nextIndex + 1,
              DEMO_DESIGN_QUESTIONS.size());
          sleepForDemoPacing(sessionId);
          redisSessionService.setLastQuestion(sessionId, demoNext);
          realtimeMessagePublisher.publishNextQuestion(sessionId, demoNext);
        } else {
          log.info("[SessionID: {}] 데모 케이스 - 마지막 질문 답변 완료, 추가 질문 없음", sessionId);
        }
        return;
      }

      String nextQuestion =
          aiGrpcClient.generateFollowUpQuestion(sessionId, previousQuestion, answer);
      redisSessionService.setLastQuestion(sessionId, nextQuestion);
      realtimeMessagePublisher.publishNextQuestion(sessionId, nextQuestion);
    } catch (Exception e) {
      log.error("[SessionID: {}] 꼬리 질문 생성 실패", sessionId, e);
      realtimeMessagePublisher.publishSessionError(sessionId, ErrorCode.AI_RESPONSE_FAILED);
    } finally {
      redisSessionService.unlockQuestionGeneration(sessionId);
    }
  }

  private Map<String, Object> toPayload(QuestionItem question) {
    return Map.of(
        "index", question.getIndex(),
        "question", question.getQuestion(),
        "tooltip", question.getTooltip(),
        "category", question.getCategory(),
        "intent", question.getIntent(),
        "answerKeywords", question.getAnswerKeywordsList());
  }
}
