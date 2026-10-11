package com.ispf.core.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
            if (!mapEqual(left.rows().get(i), right.rows().get(i))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("ReferenceEquality") // identity short-circuit before deep field compare
    private static boolean mapEqual(Map<String, Object> left, Map<String, Object> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : left.entrySet()) {
            if (!right.containsKey(entry.getKey())) {
                return false;
            }
            if (!valueEqual(entry.getValue(), right.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("ReferenceEquality") // identity short-circuit; arrays need Arrays.equals below
    private static boolean valueEqual(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof byte[] leftBytes && right instanceof byte[] rightBytes) {
            return Arrays.equals(leftBytes, rightBytes);
        }
        if (left instanceof DataRecord leftRecord && right instanceof DataRecord rightRecord) {
            return equal(leftRecord, rightRecord);
        }
        if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
            if (leftMap.size() != rightMap.size()) {
                return false;
            }
            for (Map.Entry<?, ?> entry : leftMap.entrySet()) {
                if (!rightMap.containsKey(entry.getKey())) {
                    return false;
                }
                if (!valueEqual(entry.getValue(), rightMap.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            if (leftList.size() != rightList.size()) {
                return false;
            }
            for (int i = 0; i < leftList.size(); i++) {
                if (!valueEqual(leftList.get(i), rightList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(left, right);
    }
}
