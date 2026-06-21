package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform binding: {@code sumRecordField(tableVariable[, fieldName])}.
 * <p>
 * Sums a numeric field across all rows in a {@link FieldType#RECORD_LIST} column
 * (default column: {@code rows}).
 */
public final class SumRecordFieldBinding implements PlatformBinding {

    static final SumRecordFieldBinding INSTANCE = new SumRecordFieldBinding();

    private static final Pattern PATTERN = Pattern.compile(
            "sumRecordField\\(\\s*(" + BindingSourceHelper.IDENT + ")\\s*(?:,\\s*(\""
                    + "[^\"]*\"|" + BindingSourceHelper.IDENT + ")\\s*)?\\)"
    );

    private SumRecordFieldBinding() {
    }

    @Override
    public boolean matches(String expression) {
        return expression != null && PATTERN.matcher(expression.trim()).matches();
    }

    @Override
    public Optional<Object> evaluate(
            PlatformObject object,
            String targetVariable,
            String expression,
            BindingEvaluationContext context
    ) {
        Matcher matcher = PATTERN.matcher(expression.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String tableVariable = matcher.group(1);
        String fieldName = parseFieldName(matcher.group(2), "int");
        return BindingSourceHelper.readSourceRecord(object, tableVariable)
                .map(record -> sumField(record, fieldName));
    }

    private static long sumField(DataRecord record, String fieldName) {
        String listField = record.schema().fields().stream()
                .filter(field -> field.type() == FieldType.RECORD_LIST)
                .map(FieldDefinition::name)
                .findFirst()
                .orElse("rows");
        Object rowsObject = record.rowCount() > 0 ? record.firstRow().get(listField) : null;
        if (!(rowsObject instanceof List<?> rows)) {
            return 0L;
        }
        long sum = 0L;
        for (Object rowObject : rows) {
            if (rowObject instanceof Map<?, ?> row) {
                Object value = row.get(fieldName);
                if (value instanceof Number number) {
                    sum += number.longValue();
                }
            }
        }
        return sum;
    }

    private static String parseFieldName(String raw, String defaultField) {
        if (raw == null || raw.isBlank()) {
            return defaultField;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
