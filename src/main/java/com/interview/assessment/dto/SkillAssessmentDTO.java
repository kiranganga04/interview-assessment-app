package com.interview.assessment.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SkillAssessmentDTO {
    private Long skillAssessmentId;
    private String panelType;      // INTERNAL | CLIENT
    private Integer skillOrder;
    private String skillName;
    private BigDecimal selfRating;
    private BigDecimal rating;
    private String feedback;
}
