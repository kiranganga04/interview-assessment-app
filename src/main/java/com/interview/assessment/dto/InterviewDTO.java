package com.interview.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate DTO representing one full "Interview Assessment Form" submission:
 * candidate + interview meta + internal panel skills + client panel skills + coding round(s).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewDTO {

    private Long interviewId;

    @NotNull(message = "candidateId is mandatory")
    private Long candidateId;

    // convenience read-only fields, populated on responses
    private String candidateName;
    private String mobileNumber;
    private String overallExperience;
    private String currentRole;

    private String panelMemberName;
    private String recruiterName;
    private String levelOfInterview;   // L1 | L2 | L3 | HR | CLIENT
    private String modeOfInterview;    // VIRTUAL | IN_PERSON | TELEPHONIC
    private LocalDate interviewDate;

    private String domainKnowledge;
    private String domainFeedback;

    private BigDecimal communicationRating;
    private BigDecimal finalRating;

    private String overallAssessment;
    private String panelRecommendation;
    private String interviewScreenshotUrl;

    @Valid
    @Builder.Default
    private List<SkillAssessmentDTO> internalSkillAssessments = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<SkillAssessmentDTO> clientSkillAssessments = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<CodingRoundDTO> codingRounds = new ArrayList<>();
}
