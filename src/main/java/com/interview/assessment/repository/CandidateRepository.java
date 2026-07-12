package com.interview.assessment.repository;

import com.interview.assessment.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CandidateRepository extends JpaRepository<Candidate, Long>, JpaSpecificationExecutor<Candidate> {
    Page<Candidate> findByCandidateNameContainingIgnoreCase(String name, Pageable pageable);
}
