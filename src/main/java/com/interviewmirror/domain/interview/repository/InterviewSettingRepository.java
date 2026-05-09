package com.interviewmirror.domain.interview.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.interviewmirror.domain.interview.entity.InterviewSetting;
import java.util.Optional;

public interface InterviewSettingRepository extends JpaRepository<InterviewSetting, Long> {
    // 특정 유저의 가장 최근 설정을 불러오기 위한 메서드
    Optional<InterviewSetting> findTopByUserIdOrderBySettingIdDesc(Long userId);
}
