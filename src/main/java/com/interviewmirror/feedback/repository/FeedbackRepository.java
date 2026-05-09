package com.interviewmirror.domain.feedback.repository;

import com.interviewmirror.domain.feedback.entity.FeedbackResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<FeedbackResult, Long> {

  Optional<FeedbackResult> findBySessionId(String sessionId);
}
