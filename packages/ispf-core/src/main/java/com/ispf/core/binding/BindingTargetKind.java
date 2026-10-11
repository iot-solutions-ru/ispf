package com.ispf.core.binding;


import java.util.Locale;
/**
 * Effect kind for a platform rule ({@link BindingRule}).
 */
public final class BindingTargetKind {

    public static final String VARIABLE = "variable";
    public static final String CONTEXT = "context";
    public static final String EVENT = "event";
    /** Evaluate expression only (call, write, queryRows side effects — no target write). */
    public static final String ACTION = "action";

    private BindingTargetKind() {
    }

    public static String normalize(String kind) {
        if (kind == null || kind.isBlank()) {
            return VARIABLE;
        }
        String normalized = kind.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case VARIABLE -> VARIABLE;
            case CONTEXT -> CONTEXT;
            case EVENT -> EVENT;
            case ACTION -> ACTION;
            default -> throw new IllegalArgumentException("Unknown binding target kind: " + kind);
        };
    }
}
