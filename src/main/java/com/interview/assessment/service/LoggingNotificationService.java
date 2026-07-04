package com.interview.assessment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default NotificationService: logs instead of sending real email/SMS, so the app runs
 * out of the box without SMTP credentials configured. Because it's behind the
 * NotificationService interface, plugging in Spring Mail later is a one-class change.
 */
@Slf4j
@Service
public class LoggingNotificationService implements NotificationService {

    @Override
    public void interviewScheduled(String recipientEmail, String candidateName, String scheduledAt) {
        log.info("[notification] Interview scheduled for candidate '{}' at {} — would notify {}",
                candidateName, scheduledAt, recipientEmail);
    }

    @Override
    public void interviewStatusChanged(String recipientEmail, String candidateName, String newStatus) {
        log.info("[notification] Interview for candidate '{}' moved to status {} — would notify {}",
                candidateName, newStatus, recipientEmail);
    }

    @Override
    public void passwordResetRequested(String recipientEmail, String resetToken) {
        log.info("[notification] Password reset requested for {} — reset link token: {}", recipientEmail, resetToken);
    }
}
