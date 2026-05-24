package com.interviewmirror.interview.repository;

import com.interviewmirror.interview.entity.InterviewResult;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewResultRepository extends JpaRepository<InterviewResult, Long> {
  List<InterviewResult> findByUserId(Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM InterviewResult r WHERE r.sessionId = :sessionId")
  Optional<InterviewResult> findByIdForUpdate(@Param("sessionId") Long sessionId);
}
