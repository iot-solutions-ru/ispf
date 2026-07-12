package com.ispf.core.object;

/**
 * Whether a variable's live value is persisted across server restarts.
 */
public enum VariableStorageMode {
    /** Value stored in {@code object_variables.value_json} (default). */
    PERSISTENT,
    /** Schema and policy persisted; live value is RAM-only (restarts clear value). */
    TRANSIENT;

    public static VariableStorageMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PERSISTENT;
        }
        return VariableStorageMode.valueOf(raw.trim().toUpperCase());
    }
}
