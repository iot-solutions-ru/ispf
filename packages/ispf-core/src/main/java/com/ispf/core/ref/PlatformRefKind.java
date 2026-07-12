package com.ispf.core.ref;

/**
 * Kind of tree entity addressed by {@link PlatformRef}.
 */
public enum PlatformRefKind {
    VARIABLE,
    FUNCTION,
    EVENT,
    TAG;

    public static PlatformRefKind fromSegment(String segment) {
        if (segment == null) {
            return VARIABLE;
        }
        return switch (segment) {
            case "fn" -> FUNCTION;
            case "evt" -> EVENT;
            case "tag" -> TAG;
            default -> VARIABLE;
        };
    }

    public String segment() {
        return switch (this) {
            case VARIABLE -> null;
            case FUNCTION -> "fn";
            case EVENT -> "evt";
            case TAG -> "tag";
        };
    }
}
