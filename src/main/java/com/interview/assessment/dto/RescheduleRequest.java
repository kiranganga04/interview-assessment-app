package com.interview.assessment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Payload for the explicit "Reschedule" action (PATCH /api/interviews/{id}/reschedule).
 * scheduledAt is the new date & time; meetingLink optionally replaces the existing link; reason
 * is an optional note recorded in the audit trail. Distinct from a plain edit so moving an
 * interview is an explicit, audited intent rather than a side effect of saving the form.
 */
@Data
public class RescheduleRequest {

    @NotNull
    private LocalDateTime scheduledAt;

    private String meetingLink;

    private String reason;
}
