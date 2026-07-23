package com.interview.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin Dashboard "Recruiter Workload" row: how many interviews each recruiter owns
 * (created_by / recruiter_email), with the in-flight subset broken out. ADMIN-only —
 * built over the admin's full (unscoped-for-admin) interview set in ReportService.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecruiterWorkloadDTO {
    /** The owner key the interviews were grouped by (recruiter_email, else created_by). */
    private String recruiterEmail;
    /** Display name: the matching AppUser's full name, else the interview's recruiter_name, else the email. */
    private String name;
    /** Interviews still in flight (SCHEDULED / IN_PROGRESS / SUBMITTED). */
    private long activeCount;
    /** All interviews owned by this recruiter, any status. */
    private long totalCount;
}
