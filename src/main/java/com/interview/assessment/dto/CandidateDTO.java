package com.interview.assessment.dto;

import lombok.Data;

@Data
public class CandidateDTO {
    private Long candidateId;
    private String candidateName;
    private String email;
    private String mobileNumber;
    private String overallExperience;
    private String currentRole;
}
