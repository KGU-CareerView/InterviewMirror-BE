package com.interviewmirror.interview.repository;

import com.interviewmirror.interview.entity.InterviewSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSettingRepository extends JpaRepository<InterviewSetting, Long> {
  Optional<InterviewSetting> findTopByUserIdOrderBySettingIdDesc(Long userId);
}
