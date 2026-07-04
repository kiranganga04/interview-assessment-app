package com.interview.assessment.service;

/**
 * Module 3 (workflow) notification hook. LoggingNotificationService is the default,
 * dependency-free implementation; swap in a JavaMailSender-backed implementation once
 * real SMTP credentials are available (see application.yml spring.mail.* placeholders).
 */
public interface NotificationService {
    void interviewScheduled(String recipientEmail, String candidateName, String scheduledAt);

    void interviewStatusChanged(String recipientEmail, String candidateName, String newStatus);

    void passwordResetRequested(String recipientEmail, String resetToken);
}
