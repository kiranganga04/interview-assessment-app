package com.interview.assessment.service;

/**
 * SUPERSEDED / INACTIVE — kept only as a placeholder because this environment cannot delete
 * files on your machine (only overwrite them). This class is deliberately NOT a Spring bean:
 * no @Service, no @ConditionalOnProperty, and it no longer implements NotificationService.
 *
 * Previously this and EmailNotificationService were two competing @ConditionalOnProperty-gated
 * NotificationService beans (one active when app.mail.enabled=false, the other when it was
 * true). In a real run that produced:
 *   "No qualifying bean of type 'NotificationService' available"
 * i.e. NEITHER conditional matched, most likely an interaction between
 * @ConditionalOnProperty's bean-skip evaluation and the extra property source loaded via
 * spring.config.import (backend/application-local.yml) -- the condition is evaluated before
 * that imported file's properties are guaranteed to be visible to it.
 *
 * The fix was to delete this class's role entirely and collapse to a single, always-registered
 * NotificationService bean: see EmailNotificationService, which now reads app.mail.enabled via
 * a plain @Value boolean and decides at runtime (inside its dispatch() method) whether to send
 * a real email via JavaMailSender or just log it. That removes the "zero qualifying beans"
 * failure mode completely, since there is always exactly one NotificationService bean.
 *
 * You can safely delete this file from disk; it is inert and referenced by nothing.
 */
final class LoggingNotificationService {

    private LoggingNotificationService() {
    }
}
