package com.interview.assessment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "skill_assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_assessment_id")
    private Long skillAssessmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Enumerated(EnumType.STRING)
    @Column(name = "panel_type", nullable = false)
    private PanelType panelType;

    @Column(name = "skill_order", nullable = false)
    private Integer skillOrder;

    @Column(name = "skill_name", length = 150)
    private String skillName;

    @Column(name = "self_rating")
    private BigDecimal selfRating;

    @Column(name = "rating")
    private BigDecimal rating;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;
}
