package com.interviewmirror.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewmirror.interview.entity.InterviewDetail;
import com.interviewmirror.interview.entity.InterviewReport;
import com.interviewmirror.interview.entity.InterviewResult;
import com.interviewmirror.interview.repository.InterviewDetailRepository;
import com.interviewmirror.interview.repository.InterviewReportRepository;
import com.interviewmirror.interview.repository.InterviewResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest // JPA 관련 설정만 로드하여 빠르고 가볍게 DB 테스트 수행
@ActiveProfiles("test") // application-test.yml 사용 (H2 인메모리 DB)
class InterviewRepositoryTest {

  @Autowired private InterviewResultRepository resultRepository;

  @Autowired private InterviewDetailRepository detailRepository;

  @Autowired private InterviewReportRepository reportRepository;

  @Test
  @DisplayName("InterviewResult 생성 시 자식 엔티티(Detail)가 같이 저장되는지 검증 (Cascade)")
  void saveResultWithDetails_Success() {
    // given
    InterviewResult result = InterviewResult.builder().userId(1L).sessionState("END").build();

    InterviewDetail detail1 =
        InterviewDetail.builder().interviewResult(result).question("Spring Boot의 장점은?").build();

    result.getDetails().add(detail1); // 양방향 연관관계 세팅

    // when
    InterviewResult savedResult = resultRepository.save(result);

    // then
    assertThat(savedResult.getSessionId()).isNotNull();
    assertThat(savedResult.getDetails()).hasSize(1);

    // 부모(Result)만 저장했는데 자식(Detail)도 DB에 잘 저장되었는지 확인
    assertThat(detailRepository.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("InterviewReport와 InterviewResult의 1:1 매핑(@MapsId) 검증")
  void saveReport_Success() {
    // given
    InterviewResult result =
        resultRepository.save(InterviewResult.builder().userId(2L).sessionState("END").build());

    InterviewReport report =
        InterviewReport.builder()
            .interviewResult(result) // 1:1 연결
            .totalScore(85)
            .feedback("아주 훌륭한 답변이었습니다.")
            .build();

    // when
    InterviewReport savedReport = reportRepository.save(report);

    // then
    // @MapsId가 잘 작동했다면 Report의 ID와 Result의 ID가 완벽히 같아야 함
    assertThat(savedReport.getSessionId()).isEqualTo(result.getSessionId());
    assertThat(savedReport.getTotalScore()).isEqualTo(85);
  }
}
