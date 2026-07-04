package com.interview.assessment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "skill_assessments")
public class SkillAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_assessment_id")
    private Long skillAssessmentId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Enumerated(EnumType.STRING)
    @Column(name = "panel_type", nullable = false, length = 20)
    private PanelType panelType = PanelType.INTERNAL;

    @Column(name = "skill_order", nullable = false)
    private Integer skillOrder = 1;

    /** Module 4: optional link back to the configurable skill catalog. Free-text skillName is kept for backwards compatibility. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(name = "skill_name", length = 150)
    private String skillName;

    @Column(name = "self_rating", precision = 3, scale = 1)
    private BigDecimal selfRating;

    @Column(name = "rating", precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;
}
