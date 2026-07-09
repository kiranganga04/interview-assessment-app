package com.interview.assessment.service;

/**
 * Module 3 (workflow) notification hook. LoggingNotificationService is the default,
 * dependency-free implementation; swap in a JavaMailSender-backed implementation once
 * real SMTP credentials are available (see application.yml spring.mail.* placeholders).
 */
public interface NotificationService {
    void interviewScheduled(String recipientEmail, String candidateName, String scheduledAt);

    /**
     * Richer version used by the Schedule Interview wizard (InterviewService.scheduleFromSlot):
     * called once per recipient (interviewer, candidate, recruiter) so each gets their own email
     * with the full session details, including the meeting link. recipientLabel is one of
     * "interviewer" / "candidate" / "recruiter" and only affects the greeting line.
     */
    void interviewScheduled(String recipientEmail, String recipientLabel, String candidateName,
            String position, String levelOfInterview, String scheduledAt, String meetingLink);

    void interviewStatusChanged(String recipientEmail, String candidateName, String newStatus);

    void passwordResetRequested(String recipientEmail, String resetToken);
}
