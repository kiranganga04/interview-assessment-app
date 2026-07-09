package com.interview.assessment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload for the Schedule Interview wizard: pick a candidate and an AVAILABLE slot: the
 * slot determines interviewer, date/time and mode, so the caller doesn't repeat them.
 *
 * recruiterEmail is required -- it's the third recipient (alongside the interviewer and
 * candidate) of the confirmation email sent by InterviewService.scheduleFromSlot. meetingLink
 * is optional here at the DTO level; InterviewService enforces it only when the booked slot's
 * mode is VIRTUAL (a phone or in-person slot doesn't need one).
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

    @NotBlank
    @Email
    private String recruiterEmail;

    private String meetingLink;
}
