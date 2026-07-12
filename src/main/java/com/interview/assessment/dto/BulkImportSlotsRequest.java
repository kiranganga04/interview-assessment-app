package com.interview.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Interview Management: raw CSV text uploaded from the Bulk Import page, parsed row-by-row in InterviewSlotService. */
@Data
public class BulkImportSlotsRequest {

    @NotBlank
    private String csv;
}
