package com.interviewmirror.interview.repository;

import com.interviewmirror.interview.entity.InterviewResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewResultRepository extends JpaRepository<InterviewResult, Long> {
  List<InterviewResult> findByUserId(Long userId);
}
