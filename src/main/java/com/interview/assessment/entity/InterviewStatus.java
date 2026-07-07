package com.interview.assessment.entity;

/**
 * Module 3 (workflow): lifecycle of an interview assessment record.
 * Transitions are validated in InterviewService#changeStatus rather than left free-form.
 */
public enum InterviewStatus {
    SCHEDULED,
    IN_PROGRESS,
    SUBMITTED,
    RECOMMENDED,
    CLOSED,
    /** Added alongside the Interview Slots scheduling module; releases the booked slot back to AVAILABLE. */
    CANCELLED
}
