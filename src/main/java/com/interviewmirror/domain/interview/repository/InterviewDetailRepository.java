package com.interviewmirror.domain.interview.repository;

import com.interviewmirror.domain.interview.entity.InterviewDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewDetailRepository extends JpaRepository<InterviewDetail, Long> {

  List<InterviewDetail> findByInterviewResult_SessionId(Long sessionId);
}
