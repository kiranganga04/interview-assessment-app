package com.interview.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin Dashboard "Data Hygiene" card: cheap data-quality signals only an admin can act on.
 * ADMIN-only. Built in ReportService over the admin's full interview/interviewer/skill sets.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataHygieneDTO {
    /** Legacy/unassigned interviews with neither created_by nor recruiter_email — visible to ADMIN only. */
    private long orphanedInterviews;
    /** Interviewers in the directory whose email has no matching AppUser login (can't sign in to give feedback). */
    private long interviewersWithoutUser;
    /** Active entries in the skill catalog. */
    private long activeSkillCount;
}
