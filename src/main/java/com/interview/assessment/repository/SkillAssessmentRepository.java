package com.interview.assessment.repository;

import com.interview.assessment.entity.PanelType;
import com.interview.assessment.entity.SkillAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillAssessmentRepository extends JpaRepository<SkillAssessment, Long> {
    List<SkillAssessment> findByInterview_InterviewIdAndPanelTypeOrderBySkillOrderAsc(
            Long interviewId, PanelType panelType);

    void deleteByInterview_InterviewIdAndPanelType(Long interviewId, PanelType panelType);
}
