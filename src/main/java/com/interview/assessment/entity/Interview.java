package com.interview.assessment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

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
    @Column(name = "level_of_interview")
    private InterviewLevel levelOfInterview;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_of_interview")
    private InterviewMode modeOfInterview;

    @Column(name = "interview_date")
    private LocalDate interviewDate;

    @Column(name = "domain_knowledge")
    private String domainKnowledge;

    @Column(name = "domain_feedback")
    private String domainFeedback;

    @Column(name = "communication_rating")
    private BigDecimal communicationRating;

    @Column(name = "final_rating")
    private BigDecimal finalRating;

    @Column(name = "overall_assessment", columnDefinition = "TEXT")
    private String overallAssessment;

    @Column(name = "panel_recommendation", length = 100)
    private String panelRecommendation;

    @Column(name = "interview_screenshot_url", length = 500)
    private String interviewScreenshotUrl;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillAssessment> skillAssessments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CodingRound> codingRounds = new ArrayList<>();
}
