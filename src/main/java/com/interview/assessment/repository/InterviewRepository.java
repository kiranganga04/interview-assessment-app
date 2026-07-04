package com.interview.assessment.repository;

import com.interview.assessment.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Module 8: JpaSpecificationExecutor lets InterviewService compose dynamic
 * candidate/level/status/date filters without a repository method per combination.
 */
public interface InterviewRepository extends JpaRepository<Interview, Long>, JpaSpecificationExecutor<Interview> {
}
