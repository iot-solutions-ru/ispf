package com.ispf.core.model;

import java.util.Objects;

/** Semantic equality for historian and telemetry deduplication. */
public final class DataRecordValues {

    private DataRecordValues() {
    }

    public static boolean equal(DataRecord left, DataRecord right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.rowCount() != right.rowCount()) {
            return false;
        }
        for (int i = 0; i < left.rowCount(); i++) {
            if (!Objects.equals(left.rows().get(i), right.rows().get(i))) {
                return false;
            }
        }
        return true;
    }
}
