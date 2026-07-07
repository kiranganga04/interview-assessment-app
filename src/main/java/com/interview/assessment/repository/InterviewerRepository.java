package com.interview.assessment.repository;

import com.interview.assessment.entity.Interviewer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewerRepository extends JpaRepository<Interviewer, Long> {
    boolean existsByEmailIgnoreCase(String email);
}
