package com.interview.assessment.repository;

import com.interview.assessment.entity.Interview;
import com.interview.assessment.entity.Interviewer;
import com.interview.assessment.entity.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Module 8: JpaSpecificationExecutor lets InterviewService compose dynamic
 * candidate/level/status/date filters (and the RBAC visibility filter in
 * InterviewSpecifications) without a repository method per combination.
 */
public interface InterviewRepository extends JpaRepository<Interview, Long>, JpaSpecificationExecutor<Interview> {

    /** Feedback & Reports: a Panel member's open (not-yet-submitted) assigned interviews. */
    List<Interview> findByInterviewerAndStatusIn(Interviewer interviewer, List<InterviewStatus> statuses);

    /**
     * Feedback & Reports: a Panel member's full interview history -- every interview assigned to
     * them as the interviewer, any status, most recent first (ordered by id as a stable proxy for
     * creation order since scheduled_at can be null on older records).
     */
    List<Interview> findByInterviewerOrderByInterviewIdDesc(Interviewer interviewer);
}
