package com.ispf.core.object;

/**
 * Per-variable historian sampling: store every update or only when {@link com.ispf.core.model.DataRecord}
 * value changes.
 */
public enum HistorySampleMode {
    /** Skip historian write when the new record equals the last stored sample. */
    CHANGES_ONLY,
    /** Store every accepted update (still subject to platform {@code min-interval-ms}). */
    ALL_VALUES;

    public static HistorySampleMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CHANGES_ONLY;
        }
        return HistorySampleMode.valueOf(raw.trim().toUpperCase());
    }
}
