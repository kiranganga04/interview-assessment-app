package com.interview.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CandidateDTO {
    private Long candidateId;

    @NotBlank(message = "Candidate name is mandatory")
    private String candidateName;

    private String mobileNumber;
    private String overallExperience;
    private String currentRole;
}
