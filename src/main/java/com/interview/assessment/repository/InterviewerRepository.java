package com.interview.assessment.repository;

import com.interview.assessment.entity.Interviewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface InterviewerRepository extends JpaRepository<Interviewer, Long>, JpaSpecificationExecutor<Interviewer> {
    boolean existsByEmailIgnoreCase(String email);

    /** Feedback & Reports: resolves a logged-in Panel member's login email to their Interviewer directory row. */
    Optional<Interviewer> findByEmailIgnoreCase(String email);
}
