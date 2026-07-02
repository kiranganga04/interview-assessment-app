package com.interview.assessment.repository;

import com.interview.assessment.entity.Interview;
import com.interview.assessment.entity.InterviewLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByCandidate_CandidateId(Long candidateId);
    List<Interview> findByLevelOfInterview(InterviewLevel level);
    List<Interview> findByPanelRecommendationContainingIgnoreCase(String recommendation);
}
