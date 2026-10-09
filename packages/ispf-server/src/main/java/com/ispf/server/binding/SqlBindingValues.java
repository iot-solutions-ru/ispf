package com.ispf.server.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.driver.TelemetryQuality;
import org.springframework.core.NestedExceptionUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Value handling shared by tree and application SQL bindings. A refresh that yields no usable value must not
 * look like data: the target keeps its last value, marked {@code quality=BAD} (ADR-0025).
 */
public final class SqlBindingValues {

    public static final String QUALITY_FIELD = "quality";

    private static final int MAX_DETAIL_VALUE_LENGTH = 64;

    /** Why a refresh produced no value; also the {@code reason} tag of the failure metric. */
    public enum Failure {
        NO_DATA("no_data"),
        BAD_VALUE("bad_value"),
        QUERY_FAILED("query_failed");

        private final String tag;

        Failure(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    /** What a refresh does when the query itself throws. */
    public enum OnQueryFailure {
        /** The caller asked for this one binding (deploy, manual refresh) and gets the error. */
        PROPAGATE,
        /** One binding of a fan-out: the target keeps its last value with {@code quality=BAD}. */
        MARK_BAD
    }

    /** A converted value, or the failure that left the binding without one. */
    public record Extracted(Object value, Failure failure, String detail) {

        public static Extracted of(Object value) {
            return new Extracted(value, null, null);
        }

        public static Extracted noData(String detail) {
            return new Extracted(null, Failure.NO_DATA, detail);
        }

        public static Extracted badValue(String detail) {
            return new Extracted(null, Failure.BAD_VALUE, detail);
        }

        /** The detail is the most specific cause: Spring's bad-grammar exception names the statement, not the error. */
        public static Extracted queryFailed(RuntimeException failure) {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(failure);
            String message = cause.getMessage() != null && !cause.getMessage().isBlank()
                    ? cause.getMessage()
                    : cause.getClass().getSimpleName();
            return new Extracted(null, Failure.QUERY_FAILED, "query failed: " + message);
        }

        public boolean ok() {
            return failure == null;
        }
    }

    private SqlBindingValues() {
    }

    public static Extracted toDouble(String field, Object raw) {
        if (raw == null) {
            return Extracted.noData("column '" + field + "' is NULL");
        }
        double value;
        if (raw instanceof Number number) {
            value = number.doubleValue();
        } else {
            try {
                value = Double.parseDouble(raw.toString().trim());
            } catch (NumberFormatException ex) {
                return Extracted.badValue("column '" + field + "' is not a number: " + describe(raw));
            }
        }
        if (!Double.isFinite(value)) {
            return Extracted.badValue("column '" + field + "' is not a finite number: " + describe(raw));
        }
        return Extracted.of(value);
    }

    public static Extracted toLong(String field, Object raw) {
        if (raw == null) {
            return Extracted.noData("column '" + field + "' is NULL");
        }
        if (raw instanceof Number number) {
            return Extracted.of(number.longValue());
        }
        try {
            return Extracted.of(Long.parseLong(raw.toString().trim()));
        } catch (NumberFormatException ex) {
            return Extracted.badValue("column '" + field + "' is not an integer: " + describe(raw));
        }
    }

    /**
     * {@code previous} marked {@code quality=BAD}. The value is kept, so consumers that ignore quality see no
     * change, while charts show a gap.
     */
    public static DataRecord badQuality(DataRecord previous, FieldType valueType) {
        String schemaName = "sqlBindingValue";
        FieldType type = valueType;
        Object value = null;
        if (previous != null) {
            schemaName = previous.schema().name();
            type = previous.schema().field("value").map(FieldDefinition::type).orElse(valueType);
            if (previous.rowCount() > 0) {
                value = previous.firstRow().get("value");
            }
        }
        DataSchema schema = DataSchema.builder(schemaName)
                .field("value", type)
                .field(QUALITY_FIELD, FieldType.STRING)
                .build();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", value);
        row.put(QUALITY_FIELD, TelemetryQuality.Level.BAD.apiName());
        return DataRecord.single(schema, row);
    }

    public static boolean isBadQuality(DataRecord record) {
        return record != null
                && record.rowCount() > 0
                && TelemetryQuality.Level.BAD.apiName().equals(record.firstRow().get(QUALITY_FIELD));
    }

    private static String describe(Object raw) {
        String text = String.valueOf(raw);
        return text.length() <= MAX_DETAIL_VALUE_LENGTH
                ? "'" + text + "'"
                : "'" + text.substring(0, MAX_DETAIL_VALUE_LENGTH) + "…'";
    }
}
