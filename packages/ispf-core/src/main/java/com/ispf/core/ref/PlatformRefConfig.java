package com.ispf.core.ref;

/**
 * Dual-read helper for JSON configs: {@code ref} string or objectPath + name fields.
 */
public final class PlatformRefConfig {

    private PlatformRefConfig() {
    }

    public static PlatformRef requireVariable(
            String ref,
            String objectPath,
            String variableName,
            String field
    ) {
        return PlatformRefParser.fromJsonFields(ref, objectPath, variableName, field, PlatformRefKind.VARIABLE)
                .orElseThrow(() -> new PlatformRefParseException("Variable ref is required"));
    }

    public static PlatformRef requireFunction(
            String ref,
            String objectPath,
            String functionName
    ) {
        return PlatformRefParser.fromJsonFields(ref, objectPath, functionName, null, PlatformRefKind.FUNCTION)
                .orElseThrow(() -> new PlatformRefParseException("Function ref is required"));
    }

    public static PlatformRef requireEvent(
            String ref,
            String objectPath,
            String eventName
    ) {
        return PlatformRefParser.fromJsonFields(ref, objectPath, eventName, null, PlatformRefKind.EVENT)
                .orElseThrow(() -> new PlatformRefParseException("Event ref is required"));
    }

    public static String refOrFormat(PlatformRef parsed) {
        return parsed != null ? PlatformRefFormatter.format(parsed) : null;
    }
}
