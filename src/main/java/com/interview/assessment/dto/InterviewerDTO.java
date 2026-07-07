package com.interview.assessment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InterviewerDTO {
    private Long interviewerId;

    @NotBlank
    private String fullName;

    @NotBlank @Email
    private String email;

    private String contactNumber;
    private String account;
    private String grade;
    private String levelCapability;
    private String skillSet;
    private Boolean active;
}
