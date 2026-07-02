package com.interview.assessment.repository;

import com.interview.assessment.entity.CodingRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodingRoundRepository extends JpaRepository<CodingRound, Long> {
    List<CodingRound> findByInterview_InterviewId(Long interviewId);
}
