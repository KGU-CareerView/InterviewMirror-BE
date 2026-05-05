package com.interviewmirror.domain.interview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.interviewmirror.domain.interview.entity.InterviewResult;
import java.util.List;

public interface InterviewResultRepository extends JpaRepository<InterviewResult, Long> {
    List<InterviewResult> findByUserId(Long userId);
}
