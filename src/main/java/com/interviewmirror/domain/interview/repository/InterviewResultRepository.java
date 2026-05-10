package com.interviewmirror.domain.interview.repository;

import com.interviewmirror.domain.interview.entity.InterviewResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewResultRepository extends JpaRepository<InterviewResult, Long> {
  List<InterviewResult> findByUserId(Long userId);
}
