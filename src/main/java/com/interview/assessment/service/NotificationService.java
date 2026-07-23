package com.interview.assessment.service;

import java.util.List;

/**
 * Module 3 (workflow) notification hook. EmailNotificationService is the single implementation;
 * whether a call sends a real email or just logs it is decided at runtime by app.mail.enabled.
 */
public interface NotificationService {

    /**
     * Plain scheduling notice used by the "New assessment" create() flow. Kept as a thin
     * convenience that funnels into the same single-email path as the Schedule Interview wizard.
     */
    void interviewScheduled(String recipientEmail, String candidateName, String scheduledAt);

    /**
     * Schedule Interview wizard: sends ONE email addressed to everyone involved (candidate,
     * interviewer and recruiter together) instead of a separate message per person. Blank
     * recipients are skipped; if none are present nothing is sent.
     */
    void interviewScheduled(InterviewEmailDetails details);

    /**
     * Same as {@link #interviewScheduled(InterviewEmailDetails)} but attaches files (e.g. the
     * candidate's resume) to the message. A null/empty list behaves exactly like the no-attachment
     * overload, and a listed file that is missing on disk is skipped — a bad attachment never stops
     * the email from going out.
     */
    void interviewScheduled(InterviewEmailDetails details, List<EmailAttachment> attachments);

    /**
     * Cancellation notice sent as ONE email to the candidate, interviewer and recruiter together
     * (candidate visible, the other two BCC'd). Fired when an interview moves to CANCELLED.
     */
    void interviewCancelled(InterviewEmailDetails details);

    /**
     * Reschedule notice sent as ONE email to the candidate, interviewer and recruiter together
     * when an interview's date/time changes. {@code previousScheduledAt} is shown alongside the
     * new time so recipients can see what moved (may be null/blank).
     */
    void interviewRescheduled(InterviewEmailDetails details, String previousScheduledAt);

    /**
     * Feedback-submitted notice to the RECRUITER when a panel member submits their assessment,
     * with a link to review it. Recruiter-only -- interview feedback isn't shared with the
     * candidate or (again) the panelist.
     */
    void interviewFeedbackSubmitted(InterviewEmailDetails details);

    void interviewStatusChanged(String recipientEmail, String candidateName, String newStatus);

    void passwordResetRequested(String recipientEmail, String resetToken);

    /**
     * Everything the single "interview scheduled" email needs. Any field may be null/blank
     * (e.g. the plain create() flow has no interviewer); the template and recipient list handle
     * missing values gracefully.
     */
    record InterviewEmailDetails(
            Long interviewId,
            String candidateEmail,
            String candidateName,
            String interviewerEmail,
            String interviewerName,
            String recruiterEmail,
            String recruiterName,
            String position,
            String levelOfInterview,
            String mode,
            String scheduledAt,
            String meetingLink) {
    }

    /** A file to attach to an outgoing email: the display filename plus its absolute path on disk. */
    record EmailAttachment(String filename, String path) {
    }
}
