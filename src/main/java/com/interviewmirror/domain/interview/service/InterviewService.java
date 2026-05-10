package com.interviewmirror.domain.interview.service;

import com.interviewmirror.domain.interview.entity.InterviewResult;
import com.interviewmirror.domain.interview.repository.InterviewResultRepository;
import com.interviewmirror.exception.ErrorCode;
import com.interviewmirror.exception.InterviewException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 데이터 조회만 하므로 읽기 전용 트랜잭션 적용
public class InterviewService {

  private final InterviewResultRepository resultRepository;

  // 결과 조회 로직
  public String getInterviewResult(Long sessionId) {
    InterviewResult result =
        resultRepository
            .findById(sessionId)
            .orElseThrow(() -> new InterviewException(ErrorCode.SESSION_EXPIRED)); // 조회 실패 시 예외 처리

    // DB에 분석된 결과(emotionGraph 등)가 있는지 확인
    if (result.getEmotionGraph() != null && !result.getEmotionGraph().isEmpty()) {
      return result.getEmotionGraph(); // 이미 분석된 데이터가 있다면 그대로 반환
    } else {
      /*
       * REST API 특성상 여기서 서버가 무한정 대기(RabbitMQ 응답 대기)하는 것은 권장되지 않습니다.
       * 대신 클라이언트에게 "현재 분석이 진행 중입니다"라는 의미의
       * 약속된 문자열(예: "PROCESSING")을 반환하거나, 특정 예외를 던지는 것이 좋습니다.
       */
      return "PROCESSING";
    }
  }

  // 사용자 과거 기록 조회 로직
  public List<Long> getHistory(Long userId) {
    // 이전에 만들어두신 findByUserId 메서드 활용
    List<InterviewResult> results = resultRepository.findByUserId(userId);

    // Entity 객체 리스트에서 Session ID만 뽑아내어 List<Long> 형태로 변환
    return results.stream().map(InterviewResult::getSessionId).collect(Collectors.toList());
  }
}
