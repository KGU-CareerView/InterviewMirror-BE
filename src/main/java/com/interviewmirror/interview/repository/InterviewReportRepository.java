package com.interviewmirror.interview.repository;

import com.interviewmirror.interview.entity.InterviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterviewReportRepository extends JpaRepository<InterviewReport, Long> {}
