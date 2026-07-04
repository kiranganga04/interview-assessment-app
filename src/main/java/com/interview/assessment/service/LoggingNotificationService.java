package com.interview.assessment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default NotificationService: logs instead of sending real email/SMS, so the app runs
 * out of the box without SMTP credentials configured. Active whenever app.mail.enabled is
 * false or unset. Set app.mail.enabled=true (plus MAIL_USERNAME/MAIL_PASSWORD) to switch to
 * EmailNotificationService instead, which sends real emails via Spring Mail.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
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
