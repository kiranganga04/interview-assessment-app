package com.interview.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One bar in the Dashboard's "Monthly Interviews" chart. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyInterviewCountDTO {
    /** e.g. "2026-07" -- sortable and locale-free; the frontend formats it for display. */
    private String month;
    private long count;
}
