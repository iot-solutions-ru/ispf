package com.ispf.analytics.engine;

import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefKind;
import com.ispf.core.ref.PlatformRefParser;

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

    public static AnalyticsSourceRef from(PlatformRef ref) {
        if (ref.kind() != PlatformRefKind.VARIABLE) {
            throw new IllegalArgumentException("Historian source must be variable ref: " + ref);
        }
        if (ref.isCurrentObject()) {
            throw new IllegalArgumentException("Historian source requires absolute object path: " + ref);
        }
        return new AnalyticsSourceRef(ref.object(), ref.name(), ref.field());
    }
}
