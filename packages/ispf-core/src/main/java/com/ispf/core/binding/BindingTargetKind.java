package com.ispf.core.binding;

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
        return switch (kind.trim().toLowerCase()) {
            case CONTEXT -> CONTEXT;
            case EVENT -> EVENT;
            case ACTION -> ACTION;
            default -> VARIABLE;
        };
    }
}
