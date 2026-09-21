package com.ispf.plugin.blueprint;

/**
 * Alert rule template contributed by a blueprint and materialized per target object.
 */
public record BlueprintAlertTemplate(
        String name,
        String watchVariable,
        String conditionExpr,
        String eventName,
        String severity,
        Boolean enabled,
        Boolean edgeTrigger,
        Integer delaySeconds,
        Boolean sustainWhileTrue,
        String payloadVariable,
        Boolean ackRequired,
        Integer pollIntervalMs,
        String triggerMessage,
        String clearEventName
) {
    /** Compact constructor for the common SHAPE fields. */
    public BlueprintAlertTemplate(
            String name,
            String watchVariable,
            String conditionExpr,
            String eventName,
            Boolean enabled
    ) {
        this(
                name,
                watchVariable,
                conditionExpr,
                eventName,
                null,
                enabled,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
