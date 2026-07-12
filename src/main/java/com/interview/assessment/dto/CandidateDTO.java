package com.interview.assessment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CandidateDTO {
    private Long candidateId;

    @NotBlank
    private String candidateName;

    // Optional at creation (see Candidate entity / schema.sql) -- required specifically before
    // scheduling an interview, enforced in InterviewService. @Email allows a blank/absent value
    // through and only checks the format when something is actually submitted.
    @Email
    private String email;

    private String mobileNumber;
    private String overallExperience;
    private String currentRole;
}
