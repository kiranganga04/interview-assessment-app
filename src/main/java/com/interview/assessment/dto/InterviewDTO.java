package com.interview.assessment.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class InterviewDTO {
    private Long interviewId;

    private Long candidateId;
    // read-only convenience fields, populated on the way out so list/detail views
    // don't need a second round trip to fetch the candidate
    private String candidateName;
    private String currentRole;
    private String overallExperience;

    private String panelMemberName;
    private String recruiterName;

    // Only populated for interviews created via the Schedule Interview / slot-booking flow.
    private Long interviewerId;
    private String interviewerName;
    private Long slotId;
    private String slotCode;
    private String position;

    private String levelOfInterview;
    private String modeOfInterview;
    private LocalDate interviewDate;
    private String domainKnowledge;
    private String domainFeedback;
    private BigDecimal communicationRating;
    private BigDecimal finalRating;
    private String overallAssessment;
    private String panelRecommendation;
    private String interviewScreenshotUrl;

    /** Module 3: workflow. Defaults are applied server-side if the client omits them. */
    private String status;
    private LocalDateTime scheduledAt;

    private List<SkillAssessmentDTO> internalSkillAssessments = new ArrayList<>();
    private List<SkillAssessmentDTO> clientSkillAssessments = new ArrayList<>();
    private List<CodingRoundDTO> codingRounds = new ArrayList<>();
}
