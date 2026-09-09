package com.ispf.plugin.blueprint;

/**
 * Triggers for opt-in MIXIN reevaluation (ADR-0058). v1: object created + server ready.
 */
public enum MixinReevaluationTrigger {
    OBJECT_CREATED,
    SERVER_READY
}
