package com.ispf.analytics.engine;

import java.util.Objects;

/**
 * Historian source reference for an analytics tag (BL-203).
 */
public record AnalyticsSourceRef(String path, String variable, String field) {

    public AnalyticsSourceRef {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(variable, "variable");
        if (field == null || field.isBlank()) {
            field = "value";
        }
    }
}
