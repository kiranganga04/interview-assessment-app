package com.interview.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkillAssessmentDTO {
    private Long skillAssessmentId;
    private Integer skillOrder;
    /** Module 4: optional reference into the skill catalog; skillName is kept for free-text/back-compat. */
    private Long skillId;
    private String skillName;
    private BigDecimal selfRating;
    private BigDecimal rating;
    private String feedback;
}
