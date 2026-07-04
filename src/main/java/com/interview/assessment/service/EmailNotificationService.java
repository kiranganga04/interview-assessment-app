package com.interview.assessment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * The single NotificationService bean. Whether a call actually sends a real email or just
 * logs it is decided at RUNTIME by the app.mail.enabled flag (read via @Value below), not by
 * which of two competing beans got wired in. An earlier version of this class split "log
 * only" and "send real email" into two separate @Service classes gated by
 * @ConditionalOnProperty, one active depending on app.mail.enabled -- that failed to start at
 * all on a real run ("No qualifying bean of type NotificationService") because neither
 * conditional matched in that environment, most likely an interaction between
 * @ConditionalOnProperty's bean-skip evaluation and the extra property source loaded via
 * spring.config.import (backend/application-local.yml). Collapsing to one unconditional bean
 * with a plain @Value boolean removes that entire failure mode: there is always exactly one
 * NotificationService bean, so autowiring it can never fail, and @Value's ordinary
 * String/Boolean conversion is far simpler and better-tested than conditional bean selection.
 *
 * Enable real sending with these environment variables (see README), or by setting them in
 * the git-ignored backend/application-local.yml:
 *   APP_MAIL_ENABLED=true
 *   MAIL_USERNAME=you@gmail.com          (the Gmail account that will send the emails)
 *   MAIL_PASSWORD=xxxxxxxxxxxxxxxx        (a 16-character Gmail App Password, NOT your login password)
 *
 * Gmail requires 2-Step Verification to be turned on for the account before it will issue
 * an App Password (Google Account -> Security -> 2-Step Verification -> App passwords).
 */
@Slf4j
@Service
public class EmailNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String fromAddress;
    private final String fromName;
    private final String frontendBaseUrl;

    public EmailNotificationService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${spring.mail.username:}") String fromAddress,
            @Value("${app.mail.from-name:Interview Assessment System}") String fromName,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void interviewScheduled(String recipientEmail, String candidateName, String scheduledAt) {
        dispatch(recipientEmail,
                "Interview scheduled: " + candidateName,
                "An interview for candidate " + candidateName + " has been scheduled for " + scheduledAt + ".\n\n"
                        + "Sign in to the Interview Assessment System for full details.");
    }

    @Override
    public void interviewStatusChanged(String recipientEmail, String candidateName, String newStatus) {
        dispatch(recipientEmail,
                "Interview status updated: " + candidateName,
                "The interview status for candidate " + candidateName + " changed to " + newStatus + ".\n\n"
                        + "Sign in to the Interview Assessment System for full details.");
    }

    @Override
    public void passwordResetRequested(String recipientEmail, String resetToken) {
        String resetLink = frontendBaseUrl + "/reset-password?token=" + resetToken;
        dispatch(recipientEmail,
                "Reset your Interview Assessment System password",
                "We received a request to reset your password.\n\n"
                        + "Click the link below to choose a new password (expires in 30 minutes):\n"
                        + resetLink + "\n\n"
                        + "If you didn't request this, you can safely ignore this email.");
    }

    private void dispatch(String toEmail, String subject, String body) {
        if (!mailEnabled) {
            log.info("[notification] (app.mail.enabled=false, logging only) '{}' -> {}: {}", subject, toEmail, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setFrom(fromName + " <" + fromAddress + ">");
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("[email] Sent '{}' to {}", subject, toEmail);
        } catch (Exception ex) {
            // Never let a mail-delivery failure break the calling transaction (e.g. password
            // reset should still record the token even if the SMTP send fails/times out).
            log.error("[email] Failed to send '{}' to {}: {}", subject, toEmail, ex.getMessage(), ex);
        }
    }
}
