package com.interview.assessment.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The single NotificationService bean. Whether a call actually sends a real email or just
 * logs it is decided at RUNTIME by the app.mail.enabled flag (read via @Value below).
 *
 * Emails are sent as multipart/alternative (an HTML body with a plain-text fallback) using a
 * shared, email-client-safe template (table layout + inline CSS -- the only styling that renders
 * reliably across Gmail/Outlook/Apple Mail). All recipients of the "interview scheduled" notice
 * are put on a SINGLE message rather than mailed one by one.
 *
 * Enable real sending with these environment variables (see README), or via the git-ignored
 * backend/application-local.yml:
 *   APP_MAIL_ENABLED=true
 *   MAIL_USERNAME=you@gmail.com          (the Gmail account that will send the emails)
 *   MAIL_PASSWORD=xxxxxxxxxxxxxxxx        (a 16-character Gmail App Password, NOT your login password)
 */
@Slf4j
@Service
public class EmailNotificationService implements NotificationService {

    private static final String BRAND = "Interview Assessment System";
    private static final String ACCENT = "#1f3a8a";      // header + button
    private static final String ACCENT_DARK = "#162a63";
    private static final String INK = "#1f2430";
    private static final String MUTED = "#6b7280";
    private static final String BORDER = "#e5e7eb";
    private static final String CANVAS = "#f4f5f7";

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

    // ------------------------------------------------------------------ scheduling

    @Override
    public void interviewScheduled(String recipientEmail, String candidateName, String scheduledAt) {
        // Plain "New assessment" flow -- reuse the single-email path with just what we know.
        interviewScheduled(new InterviewEmailDetails(
                null, null, candidateName, null, null, recipientEmail, null,
                null, null, null, scheduledAt, null));
    }

    @Override
    public void interviewScheduled(InterviewEmailDetails d) {
        interviewScheduled(d, List.of());
    }

    @Override
    public void interviewScheduled(InterviewEmailDetails d, List<EmailAttachment> attachments) {
        // Candidate is the visible recipient; interviewer + recruiter are BCC'd so the parties'
        // addresses aren't exposed to each other. If there's no candidate address, dispatch()
        // promotes the first BCC recipient to the visible To so the email still goes out.
        List<String> to = recipients(d.candidateEmail());
        List<String> bcc = recipients(d.interviewerEmail(), d.recruiterEmail());
        if (to.isEmpty() && bcc.isEmpty()) {
            log.warn("[notification] Skipping interviewScheduled for '{}': no recipient emails.", d.candidateName());
            return;
        }

        String subject = "Interview scheduled: " + safe(d.candidateName())
                + (StringUtils.hasText(d.position()) ? " (" + safe(d.position()) + ")" : "");

        StringBuilder rows = new StringBuilder();
        rows.append(row("Candidate", d.candidateName()));
        rows.append(row("Position", d.position()));
        rows.append(row("Interview level", d.levelOfInterview()));
        rows.append(row("Mode", prettyMode(d.mode())));
        rows.append(row("Date & time", d.scheduledAt()));
        rows.append(row("Interviewer / panel", d.interviewerName()));
        if (StringUtils.hasText(d.meetingLink())) {
            rows.append(linkRow("Meeting link", d.meetingLink()));
        }

        String intro = "A new interview has been scheduled. The details below have been shared with the "
                + "candidate, the interview panel, and the recruiter.";
        String ctaLabel = StringUtils.hasText(d.meetingLink()) ? "Join the meeting" : "Open " + BRAND;
        String ctaUrl = StringUtils.hasText(d.meetingLink()) ? d.meetingLink() : frontendBaseUrl;

        String html = shell("Interview scheduled", "Interview Scheduled", intro, rows.toString(), ctaLabel, ctaUrl,
                "This is an automated message from " + BRAND + ". Please do not reply directly to this email.");
        String text = plainText("Interview Scheduled", intro, new String[][]{
                {"Candidate", d.candidateName()}, {"Position", d.position()}, {"Interview level", d.levelOfInterview()},
                {"Mode", prettyMode(d.mode())}, {"Date & time", d.scheduledAt()}, {"Interviewer / panel", d.interviewerName()},
                {"Meeting link", d.meetingLink()}}, ctaLabel, ctaUrl);

        dispatch(to, bcc, subject, html, text, attachments);
    }

    // ------------------------------------------------------------------ cancellation

    @Override
    public void interviewCancelled(InterviewEmailDetails d) {
        List<String> to = recipients(d.candidateEmail());
        List<String> bcc = recipients(d.interviewerEmail(), d.recruiterEmail());
        if (to.isEmpty() && bcc.isEmpty()) {
            log.warn("[notification] Skipping interviewCancelled for '{}': no recipient emails.", d.candidateName());
            return;
        }

        String subject = "Interview cancelled: " + safe(d.candidateName())
                + (StringUtils.hasText(d.position()) ? " (" + safe(d.position()) + ")" : "");

        StringBuilder rows = new StringBuilder();
        rows.append(row("Candidate", d.candidateName()));
        rows.append(row("Position", d.position()));
        rows.append(row("Interview level", d.levelOfInterview()));
        rows.append(row("Mode", prettyMode(d.mode())));
        rows.append(row("Was scheduled for", d.scheduledAt()));
        rows.append(row("Interviewer / panel", d.interviewerName()));

        String intro = "The interview below has been cancelled. No further action is needed. "
                + "If you believe this is a mistake, please contact the recruiter.";
        String html = shell("Interview cancelled", "Interview Cancelled", intro, rows.toString(),
                "Open " + BRAND, frontendBaseUrl,
                "This is an automated message from " + BRAND + ". Please do not reply directly to this email.");
        String text = plainText("Interview Cancelled", intro, new String[][]{
                {"Candidate", d.candidateName()}, {"Position", d.position()}, {"Interview level", d.levelOfInterview()},
                {"Mode", prettyMode(d.mode())}, {"Was scheduled for", d.scheduledAt()},
                {"Interviewer / panel", d.interviewerName()}}, "Open " + BRAND, frontendBaseUrl);

        dispatch(to, bcc, subject, html, text);
    }

    // ------------------------------------------------------------------ reschedule

    @Override
    public void interviewRescheduled(InterviewEmailDetails d, String previousScheduledAt) {
        List<String> to = recipients(d.candidateEmail());
        List<String> bcc = recipients(d.interviewerEmail(), d.recruiterEmail());
        if (to.isEmpty() && bcc.isEmpty()) {
            log.warn("[notification] Skipping interviewRescheduled for '{}': no recipient emails.", d.candidateName());
            return;
        }

        String subject = "Interview rescheduled: " + safe(d.candidateName())
                + (StringUtils.hasText(d.position()) ? " (" + safe(d.position()) + ")" : "");

        StringBuilder rows = new StringBuilder();
        rows.append(row("Candidate", d.candidateName()));
        rows.append(row("Position", d.position()));
        rows.append(row("Interview level", d.levelOfInterview()));
        rows.append(row("Mode", prettyMode(d.mode())));
        rows.append(row("New date & time", d.scheduledAt()));
        rows.append(row("Previous date & time", previousScheduledAt));
        rows.append(row("Interviewer / panel", d.interviewerName()));
        if (StringUtils.hasText(d.meetingLink())) {
            rows.append(linkRow("Meeting link", d.meetingLink()));
        }

        String intro = "The interview below has been rescheduled. Please note the new date and time.";
        String ctaLabel = StringUtils.hasText(d.meetingLink()) ? "Join the meeting" : "Open " + BRAND;
        String ctaUrl = StringUtils.hasText(d.meetingLink()) ? d.meetingLink() : frontendBaseUrl;
        String html = shell("Interview rescheduled", "Interview Rescheduled", intro, rows.toString(), ctaLabel, ctaUrl,
                "This is an automated message from " + BRAND + ". Please do not reply directly to this email.");
        String text = plainText("Interview Rescheduled", intro, new String[][]{
                {"Candidate", d.candidateName()}, {"Position", d.position()}, {"Interview level", d.levelOfInterview()},
                {"Mode", prettyMode(d.mode())}, {"New date & time", d.scheduledAt()},
                {"Previous date & time", previousScheduledAt}, {"Interviewer / panel", d.interviewerName()},
                {"Meeting link", d.meetingLink()}}, ctaLabel, ctaUrl);

        dispatch(to, bcc, subject, html, text);
    }

    // ------------------------------------------------------------------ feedback submitted

    @Override
    public void interviewFeedbackSubmitted(InterviewEmailDetails d) {
        // Recruiter-only: interview feedback is for the hiring side, not the candidate/panelist.
        List<String> to = recipients(d.recruiterEmail());
        if (to.isEmpty()) {
            log.warn("[notification] Skipping interviewFeedbackSubmitted for '{}': no recruiter email.", d.candidateName());
            return;
        }

        String subject = "Feedback submitted: " + safe(d.candidateName())
                + (StringUtils.hasText(d.position()) ? " (" + safe(d.position()) + ")" : "");

        StringBuilder rows = new StringBuilder();
        rows.append(row("Candidate", d.candidateName()));
        rows.append(row("Position", d.position()));
        rows.append(row("Interview level", d.levelOfInterview()));
        rows.append(row("Submitted by", d.interviewerName()));

        String reviewUrl = d.interviewId() != null ? frontendBaseUrl + "/interviews/" + d.interviewId() : frontendBaseUrl;
        String intro = "The interview panel has submitted their feedback. You can now review the full "
                + "assessment -- ratings, comments and recommendation -- in " + BRAND + ".";
        String html = shell("Feedback submitted", "Feedback Submitted", intro, rows.toString(),
                "View feedback", reviewUrl,
                "This is an automated message from " + BRAND + ". Please do not reply directly to this email.");
        String text = plainText("Feedback Submitted", intro, new String[][]{
                {"Candidate", d.candidateName()}, {"Position", d.position()}, {"Interview level", d.levelOfInterview()},
                {"Submitted by", d.interviewerName()}}, "View feedback", reviewUrl);

        dispatch(to, List.of(), subject, html, text);
    }

    // ------------------------------------------------------------------ status change

    @Override
    public void interviewStatusChanged(String recipientEmail, String candidateName, String newStatus) {
        String intro = "The status of the interview for " + safe(candidateName) + " has been updated.";
        String rows = row("Candidate", candidateName) + row("New status", prettyStatus(newStatus));
        String html = shell("Interview status updated", "Interview Status Updated", intro, rows,
                "Open " + BRAND, frontendBaseUrl,
                "This is an automated message from " + BRAND + ".");
        String text = plainText("Interview Status Updated", intro,
                new String[][]{{"Candidate", candidateName}, {"New status", prettyStatus(newStatus)}},
                "Open " + BRAND, frontendBaseUrl);
        dispatch(recipients(recipientEmail), List.of(), "Interview status updated: " + safe(candidateName), html, text);
    }

    // ------------------------------------------------------------------ password reset

    @Override
    public void passwordResetRequested(String recipientEmail, String resetToken) {
        String resetLink = frontendBaseUrl + "/reset-password?token=" + resetToken;
        String intro = "We received a request to reset your " + BRAND + " password. "
                + "Click the button below to choose a new one. This link expires in 30 minutes.";
        String html = shell("Reset your password", "Reset Your Password", intro, "", "Reset password", resetLink,
                "If you didn't request this, you can safely ignore this email -- your password won't change.");
        String text = "Reset Your Password\n\n" + intro + "\n\n" + resetLink
                + "\n\nIf you didn't request this, you can safely ignore this email.";
        dispatch(recipients(recipientEmail), List.of(), "Reset your " + BRAND + " password", html, text);
    }

    // ------------------------------------------------------------------ template

    /** Full responsive, email-client-safe HTML document (tables + inline CSS). */
    private String shell(String preheader, String heading, String introHtml, String detailsRowsHtml,
                         String ctaLabel, String ctaUrl, String footerNote) {
        StringBuilder b = new StringBuilder();
        b.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<meta name=\"color-scheme\" content=\"light\"><title>").append(esc(heading)).append("</title></head>")
         .append("<body style=\"margin:0;padding:0;background:").append(CANVAS).append(";\">")
         // hidden preheader (inbox preview text)
         .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">").append(esc(preheader)).append("</div>")
         .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
         .append(CANVAS).append(";padding:24px 0;\"><tr><td align=\"center\">")
         .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
         .append("style=\"width:600px;max-width:92%;background:#ffffff;border-radius:12px;overflow:hidden;")
         .append("border:1px solid ").append(BORDER).append(";font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;\">")
         // header
         .append("<tr><td style=\"background:").append(ACCENT).append(";padding:24px 32px;\">")
         .append("<div style=\"color:#ffffff;font-size:18px;font-weight:700;letter-spacing:.2px;\">").append(esc(BRAND)).append("</div>")
         .append("<div style=\"color:#c7d2fe;font-size:13px;margin-top:2px;\">Panel evaluation workspace</div>")
         .append("</td></tr>")
         // body
         .append("<tr><td style=\"padding:32px;\">")
         .append("<h1 style=\"margin:0 0 12px;color:").append(INK).append(";font-size:22px;\">").append(esc(heading)).append("</h1>")
         .append("<p style=\"margin:0 0 20px;color:").append(MUTED).append(";font-size:15px;line-height:1.6;\">").append(esc(introHtml)).append("</p>");

        if (StringUtils.hasText(detailsRowsHtml)) {
            b.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
             .append("style=\"border:1px solid ").append(BORDER).append(";border-radius:8px;border-collapse:separate;overflow:hidden;margin-bottom:24px;\">")
             .append(detailsRowsHtml).append("</table>");
        }

        if (StringUtils.hasText(ctaLabel) && StringUtils.hasText(ctaUrl)) {
            b.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr><td ")
             .append("style=\"border-radius:8px;background:").append(ACCENT).append(";\">")
             .append("<a href=\"").append(esc(ctaUrl)).append("\" target=\"_blank\" ")
             .append("style=\"display:inline-block;padding:12px 28px;color:#ffffff;font-size:15px;font-weight:600;")
             .append("text-decoration:none;border-radius:8px;background:").append(ACCENT).append(";border:1px solid ").append(ACCENT_DARK).append(";\">")
             .append(esc(ctaLabel)).append("</a></td></tr></table>");
        }

        b.append("</td></tr>")
         // footer
         .append("<tr><td style=\"padding:20px 32px;background:#fafafa;border-top:1px solid ").append(BORDER).append(";\">")
         .append("<p style=\"margin:0;color:").append(MUTED).append(";font-size:12px;line-height:1.5;\">").append(esc(footerNote)).append("</p>")
         .append("</td></tr>")
         .append("</table></td></tr></table></body></html>");
        return b.toString();
    }

    /** One label/value row in the details card. Returns "" when the value is blank. */
    private String row(String label, String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return "<tr>"
                + "<td style=\"padding:12px 16px;background:#f9fafb;border-bottom:1px solid " + BORDER
                + ";color:" + MUTED + ";font-size:13px;width:170px;vertical-align:top;\">" + esc(label) + "</td>"
                + "<td style=\"padding:12px 16px;border-bottom:1px solid " + BORDER
                + ";color:" + INK + ";font-size:14px;font-weight:600;\">" + esc(value) + "</td></tr>";
    }

    private String linkRow(String label, String url) {
        return "<tr>"
                + "<td style=\"padding:12px 16px;background:#f9fafb;border-bottom:1px solid " + BORDER
                + ";color:" + MUTED + ";font-size:13px;width:170px;vertical-align:top;\">" + esc(label) + "</td>"
                + "<td style=\"padding:12px 16px;border-bottom:1px solid " + BORDER + ";font-size:14px;\">"
                + "<a href=\"" + esc(url) + "\" target=\"_blank\" style=\"color:" + ACCENT + ";font-weight:600;\">"
                + esc(url) + "</a></td></tr>";
    }

    private String plainText(String heading, String intro, String[][] rows, String ctaLabel, String ctaUrl) {
        StringBuilder t = new StringBuilder();
        t.append(heading).append("\n\n").append(intro).append("\n\n");
        for (String[] r : rows) {
            if (r.length == 2 && StringUtils.hasText(r[1])) {
                t.append(r[0]).append(": ").append(r[1]).append("\n");
            }
        }
        if (StringUtils.hasText(ctaLabel) && StringUtils.hasText(ctaUrl)) {
            t.append("\n").append(ctaLabel).append(": ").append(ctaUrl).append("\n");
        }
        t.append("\n-- ").append(BRAND);
        return t.toString();
    }

    // ------------------------------------------------------------------ dispatch

    private void dispatch(List<String> to, List<String> bcc, String subject, String html, String text) {
        dispatch(to, bcc, subject, html, text, List.of());
    }

    private void dispatch(List<String> to, List<String> bcc, String subject, String html, String text,
                          List<EmailAttachment> attachments) {
        // Visible recipients.
        List<String> toList = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (String t : to) {
            if (StringUtils.hasText(t) && keys.add(t.trim().toLowerCase())) {
                toList.add(t.trim());
            }
        }
        // Hidden recipients, minus anyone already visible.
        List<String> bccList = new ArrayList<>();
        for (String b : bcc) {
            if (StringUtils.hasText(b) && keys.add(b.trim().toLowerCase())) {
                bccList.add(b.trim());
            }
        }
        // If nobody is on the visible To, promote the first hidden recipient so the mail still sends.
        if (toList.isEmpty() && !bccList.isEmpty()) {
            toList.add(bccList.remove(0));
        }
        if (toList.isEmpty()) {
            log.warn("[notification] Skipping '{}': no recipients.", subject);
            return;
        }
        List<EmailAttachment> atts = attachments == null ? List.of() : attachments;
        if (!mailEnabled) {
            log.info("[notification] (app.mail.enabled=false, logging only) '{}' -> to={} bcc={} attachments={}\n{}",
                    subject, toList, bccList, atts.stream().map(EmailAttachment::filename).toList(), text);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setTo(toList.toArray(new String[0]));
            if (!bccList.isEmpty()) {
                helper.setBcc(bccList.toArray(new String[0]));
            }
            helper.setFrom(fromAddress, fromName);
            helper.setSubject(subject);
            helper.setText(text, html); // (plainText, htmlText) -> multipart/alternative
            // Attach any files (e.g. the candidate's resume). A missing file is skipped, never fatal.
            for (EmailAttachment att : atts) {
                if (att == null || !StringUtils.hasText(att.path())) {
                    continue;
                }
                java.io.File file = new java.io.File(att.path());
                if (file.exists() && file.isFile()) {
                    String name = StringUtils.hasText(att.filename()) ? att.filename() : file.getName();
                    helper.addAttachment(name, new org.springframework.core.io.FileSystemResource(file));
                } else {
                    log.warn("[email] Attachment missing on disk, sending '{}' without it: {}", subject, att.path());
                }
            }
            mailSender.send(message);
            log.info("[email] Sent '{}' to={} bcc={} attachments={}", subject, toList, bccList,
                    atts.stream().map(EmailAttachment::filename).toList());
        } catch (Exception ex) {
            // Never let a mail-delivery failure break the calling transaction.
            log.error("[email] Failed to send '{}' to={} bcc={}: {}", subject, toList, bccList, ex.getMessage(), ex);
        }
    }

    /** De-duplicated, trimmed, non-blank recipient list (case-insensitive de-dup). */
    private List<String> recipients(String... emails) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String e : emails) {
            if (StringUtils.hasText(e) && seen.add(e.trim().toLowerCase())) {
                out.add(e.trim());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private String prettyMode(String mode) {
        if (!StringUtils.hasText(mode)) return null;
        return switch (mode.toUpperCase()) {
            case "VIRTUAL" -> "Virtual";
            case "IN_PERSON" -> "In person";
            case "TELEPHONIC" -> "Telephonic";
            default -> mode;
        };
    }

    private String prettyStatus(String status) {
        return StringUtils.hasText(status) ? status.replace('_', ' ') : status;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /** Minimal HTML escaping for user-provided values interpolated into the template. */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
