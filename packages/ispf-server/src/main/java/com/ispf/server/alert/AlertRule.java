package com.ispf.server.alert;

import java.time.Instant;

public record AlertRule(
        String id,
        String name,
        String objectPath,
        String watchVariable,
        String conditionExpr,
        String eventName,
        String payloadVariable,
        boolean enabled,
        boolean edgeTrigger,
        int delaySeconds,
        boolean sustainWhileTrue,
        int rateLimitSeconds,
        String priority,
        boolean ackRequired,
        String deactivateExpr,
        int deactivateDelaySeconds,
        int pollIntervalMs,
        String triggerMessage,
        String clearEventName,
        Boolean lastConditionMet,
        Boolean latchedActive,
        Instant conditionTrueSince,
        Instant deactivateTrueSince,
        Instant lastFiredAt,
        Instant createdAt,
        Instant updatedAt,
        String notificationWebhookUrl,
        String notificationEmailTarget,
        String anomalyModelId
) {
    public AlertPriority resolvedPriority() {
        return AlertPriority.parse(priority);
    }

    public boolean hasNotificationChannel() {
        return (notificationWebhookUrl != null && !notificationWebhookUrl.isBlank())
                || (notificationEmailTarget != null && !notificationEmailTarget.isBlank());
    }

    /** Latch semantics: hold active state until explicit clear path runs. */
    public boolean usesLatch() {
        return (deactivateExpr != null && !deactivateExpr.isBlank())
                || (clearEventName != null && !clearEventName.isBlank())
                || deactivateDelaySeconds > 0;
    }
}
