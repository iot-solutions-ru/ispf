package com.ispf.server.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SqlBindingValuesTest {

    @Test
    void numbersAndNumericTextConvert() {
        assertThat(SqlBindingValues.toDouble("value", new BigDecimal("12.5")).value()).isEqualTo(12.5);
        assertThat(SqlBindingValues.toDouble("value", " 7 ").value()).isEqualTo(7.0);
        assertThat(SqlBindingValues.toLong("value", 42).value()).isEqualTo(42L);
        assertThat(SqlBindingValues.toLong("value", "-3").value()).isEqualTo(-3L);
    }

    @Test
    void sqlNullIsNoData() {
        SqlBindingValues.Extracted extracted = SqlBindingValues.toDouble("oee", null);

        assertThat(extracted.ok()).isFalse();
        assertThat(extracted.failure()).isEqualTo(SqlBindingValues.Failure.NO_DATA);
        assertThat(extracted.detail()).isEqualTo("column 'oee' is NULL");
        assertThat(SqlBindingValues.toLong("count", null).failure()).isEqualTo(SqlBindingValues.Failure.NO_DATA);
    }

    @Test
    void unconvertibleValuesAreBadInsteadOfZero() {
        SqlBindingValues.Extracted text = SqlBindingValues.toDouble("oee", "n/a");

        assertThat(text.failure()).isEqualTo(SqlBindingValues.Failure.BAD_VALUE);
        assertThat(text.value()).isNull();
        assertThat(text.detail()).isEqualTo("column 'oee' is not a number: 'n/a'");
        assertThat(SqlBindingValues.toDouble("oee", Double.NaN).failure())
                .isEqualTo(SqlBindingValues.Failure.BAD_VALUE);
        assertThat(SqlBindingValues.toDouble("oee", "Infinity").failure())
                .isEqualTo(SqlBindingValues.Failure.BAD_VALUE);
        assertThat(SqlBindingValues.toLong("count", "1.5").failure())
                .isEqualTo(SqlBindingValues.Failure.BAD_VALUE);
    }

    @Test
    void failureDetailTruncatesLongValues() {
        String detail = SqlBindingValues.toDouble("oee", "x".repeat(500)).detail();

        assertThat(detail).hasSizeLessThan(120).endsWith("…'");
    }

    @Test
    void queryFailureNamesTheDatabaseErrorRatherThanTheStatement() {
        SqlBindingValues.Extracted failed = SqlBindingValues.Extracted.queryFailed(new BadSqlGrammarException(
                "StatementCallback", "SELECT oee FROM kpi", new SQLException("relation \"kpi\" does not exist")));

        assertThat(failed.failure()).isEqualTo(SqlBindingValues.Failure.QUERY_FAILED);
        assertThat(failed.value()).isNull();
        assertThat(failed.detail()).isEqualTo("query failed: relation \"kpi\" does not exist");
    }

    @Test
    void queryFailureWithoutAMessageNamesTheExceptionType() {
        assertThat(SqlBindingValues.Extracted.queryFailed(new IllegalStateException()).detail())
                .isEqualTo("query failed: IllegalStateException");
    }

    @Test
    void badQualityKeepsThePreviousValueAndSchemaName() {
        DataSchema schema = DataSchema.builder("doubleValue").field("value", FieldType.DOUBLE).build();
        DataRecord previous = DataRecord.single(schema, Map.of("value", 42.5));

        DataRecord bad = SqlBindingValues.badQuality(previous, FieldType.DOUBLE);

        assertThat(bad.schema().name()).isEqualTo("doubleValue");
        assertThat(bad.schema().field("value").map(FieldDefinition::type)).contains(FieldType.DOUBLE);
        assertThat(bad.firstRow()).containsEntry("value", 42.5).containsEntry("quality", "BAD");
        assertThat(SqlBindingValues.isBadQuality(bad)).isTrue();
        assertThat(SqlBindingValues.isBadQuality(previous)).isFalse();
    }

    @Test
    void badQualityWithoutAPreviousValueHasNoValue() {
        DataRecord bad = SqlBindingValues.badQuality(null, FieldType.STRING);

        assertThat(bad.schema().field("value").map(FieldDefinition::type)).contains(FieldType.STRING);
        assertThat(bad.firstRow()).containsEntry("value", null).containsEntry("quality", "BAD");
        assertThat(SqlBindingValues.isBadQuality(null)).isFalse();
    }
}
