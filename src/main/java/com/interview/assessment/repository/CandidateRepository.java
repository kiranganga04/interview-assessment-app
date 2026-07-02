package com.interview.assessment.repository;

import com.interview.assessment.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findByCandidateNameContainingIgnoreCase(String name);
}
