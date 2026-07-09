package com.interview.assessment.repository;

import com.interview.assessment.entity.Interview;
import com.interview.assessment.entity.Interviewer;
import com.interview.assessment.entity.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Module 8: JpaSpecificationExecutor lets InterviewService compose dynamic
 * candidate/level/status/date filters without a repository method per combination.
 */
public interface InterviewRepository extends JpaRepository<Interview, Long>, JpaSpecificationExecutor<Interview> {

    /** Feedback & Reports: a Panel member's open (not-yet-submitted) assigned interviews. */
    List<Interview> findByInterviewerAndStatusIn(Interviewer interviewer, List<InterviewStatus> statuses);
}
