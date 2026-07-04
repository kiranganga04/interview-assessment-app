package com.interview.assessment.repository;

import com.interview.assessment.entity.SkillAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillAssessmentRepository extends JpaRepository<SkillAssessment, Long> {
    List<SkillAssessment> findByInterview_InterviewId(Long interviewId);
}
