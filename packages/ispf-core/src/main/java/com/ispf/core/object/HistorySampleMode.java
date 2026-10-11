package com.ispf.core.object;


import java.util.Locale;
/**
 * Per-variable historian sampling: store every update or only when {@link com.ispf.core.model.DataRecord}
 * value changes.
 */
public enum HistorySampleMode {
    /** Skip historian write when the new record equals the last stored sample. */
    CHANGES_ONLY,
    /** Store every accepted update (still subject to platform {@code min-interval-ms}). */
    ALL_VALUES;

    /** Strict parse for API/input. Blank → {@link #CHANGES_ONLY}; unknown → throws. */
    public static HistorySampleMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CHANGES_ONLY;
        }
        return HistorySampleMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    /** Lenient parse for persisted rows so one bad value cannot fail tree load. */
    public static HistorySampleMode parseLenient(String raw) {
        try {
            return parse(raw);
        } catch (IllegalArgumentException ex) {
            return CHANGES_ONLY;
        }
    }
}
