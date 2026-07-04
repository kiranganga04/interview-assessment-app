package com.interview.assessment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "interviews")
public class Interview extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interview_id")
    private Long interviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(name = "panel_member_name", length = 150)
    private String panelMemberName;

    @Column(name = "recruiter_name", length = 150)
    private String recruiterName;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_of_interview", length = 20)
    private InterviewLevel levelOfInterview = InterviewLevel.L1;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_of_interview", length = 20)
    private InterviewMode modeOfInterview = InterviewMode.VIRTUAL;

    @Column(name = "interview_date")
    private LocalDate interviewDate;

    @Column(name = "domain_knowledge", length = 255)
    private String domainKnowledge;

    @Column(name = "domain_feedback", length = 255)
    private String domainFeedback;

    @Column(name = "communication_rating", precision = 3, scale = 1)
    private BigDecimal communicationRating;

    @Column(name = "final_rating", precision = 3, scale = 1)
    private BigDecimal finalRating;

    @Column(name = "overall_assessment", columnDefinition = "TEXT")
    private String overallAssessment;

    @Column(name = "panel_recommendation", length = 100)
    private String panelRecommendation;

    @Column(name = "interview_screenshot_url", length = 500)
    private String interviewScreenshotUrl;

    /** Module 3: workflow status + optional scheduled date/time. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<SkillAssessment> skillAssessments = new ArrayList<>();

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<CodingRound> codingRounds = new ArrayList<>();
}
