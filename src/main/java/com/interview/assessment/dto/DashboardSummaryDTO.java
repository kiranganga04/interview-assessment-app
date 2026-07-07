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

    // Added for the Dashboard Overview redesign -- additive, existing fields above are untouched
    // so any older consumer of this DTO keeps working unchanged.
    private long closedCount;
    private long cancelledCount;
    /** RECOMMENDED + CLOSED: interviews that have reached a concluded outcome. */
    private long completedCount;
    private long candidateCount;
    private long interviewerCount;
    private long todaysInterviewCount;
    /** Same population as submittedCount (ratings filled in, awaiting a recommendation), exposed under the dashboard's own terminology. */
    private long pendingFeedbackCount;
    /** SCHEDULED interviews whose scheduled_at has already passed. */
    private long overdueCount;
}
