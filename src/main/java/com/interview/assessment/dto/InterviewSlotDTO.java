package com.interview.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class InterviewSlotDTO {
    private Long slotId;
    private String slotCode;

    @NotNull
    private Long interviewerId;
    // read-only convenience fields, populated on the way out so the slots table
    // doesn't need a second round trip per row to look up the interviewer
    private String interviewerName;
    private String interviewerEmail;
    private String interviewerContact;
    private String account;
    private String grade;
    private String levelCapability;

    @NotNull
    private LocalDate slotDate;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotBlank
    private String mode;

    private String technology;
    private String status;
}
