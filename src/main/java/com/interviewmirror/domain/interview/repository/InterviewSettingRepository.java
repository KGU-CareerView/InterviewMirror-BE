package com.interviewmirror.domain.interview.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.interviewmirror.domain.interview.entity.InterviewSetting;
import java.util.Optional;

public interface InterviewSettingRepository extends JpaRepository<InterviewSetting, Long> {
    Optional<InterviewSetting> findTopByUserIdOrderBySettingIdDesc(Long userId);
}
