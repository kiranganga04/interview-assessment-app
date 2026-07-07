package com.interview.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload for the Schedule Interview wizard: pick a candidate and an AVAILABLE slot: the
 * slot determines interviewer, date/time and mode, so the caller doesn't repeat them.
 */
@Data
public class ScheduleInterviewRequest {
    @NotNull
    private Long candidateId;

    @NotNull
    private Long slotId;

    @NotBlank
    private String levelOfInterview;

    private String position;
    private String recruiterName;
}
