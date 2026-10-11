package com.ispf.core.object;


import java.util.Locale;
/**
 * Whether a variable's live value is persisted across server restarts.
 */
public enum VariableStorageMode {
    /** Value stored in {@code object_variables.value_json} (default). */
    PERSISTENT,
    /** Schema and policy persisted; live value is RAM-only (restarts clear value). */
    TRANSIENT;

    /** Strict parse for API/input. Blank → {@link #PERSISTENT}; unknown → throws. */
    public static VariableStorageMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PERSISTENT;
        }
        return VariableStorageMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    /** Lenient parse for persisted rows so one bad value cannot fail tree load. */
    public static VariableStorageMode parseLenient(String raw) {
        try {
            return parse(raw);
        } catch (IllegalArgumentException ex) {
            return PERSISTENT;
        }
    }
}
