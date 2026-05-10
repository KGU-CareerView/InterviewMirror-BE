package com.interviewmirror.domain.interview.repository;

import com.interviewmirror.domain.interview.entity.InterviewSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSettingRepository extends JpaRepository<InterviewSetting, Long> {
  Optional<InterviewSetting> findTopByUserIdOrderBySettingIdDesc(Long userId);
}
