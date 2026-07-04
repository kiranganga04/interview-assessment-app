package com.interview.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {
    private long totalInterviews;
    private long scheduledCount;
    private long submittedCount;
    private long recommendedCount;
    private double averageFinalRating;
}
