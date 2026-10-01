package com.ispf.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataRecordTest {

    @Test
    void createsSingleRowRecord() {
        DataSchema schema = DataSchema.builder("sensor")
                .field("value", FieldType.DOUBLE)
                .field("unit", FieldType.STRING)
                .build();

        DataRecord record = DataRecord.single(schema, Map.of("value", 21.0, "unit", "C"));

        assertThat(record.rowCount()).isEqualTo(1);
        assertThat(record.get("value", 0)).isEqualTo(21.0);
    }

    @Test
    void rejectsMissingRequiredField() {
        DataSchema schema = DataSchema.builder("sensor")
                .field(FieldDefinition.required("value", FieldType.DOUBLE))
                .build();

        assertThatThrownBy(() -> DataRecord.single(schema, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesFiniteNumbersInDoubleFieldAsDouble() {
        DataSchema schema = DataSchema.builder("sensor")
                .field("value", FieldType.DOUBLE)
                .build();

        long aboveInt = Integer.MAX_VALUE + 1L;
        DataRecord fromLong = DataRecord.single(schema, Map.of("value", aboveInt));
        DataRecord fromInt = DataRecord.single(schema, Map.of("value", 21));
        DataRecord fromFloat = DataRecord.single(schema, Map.of("value", 1.5f));

        assertThat(fromLong.get("value", 0)).isEqualTo(2147483648.0);
        assertThat(fromInt.get("value", 0)).isEqualTo(21.0);
        assertThat(fromFloat.get("value", 0)).isEqualTo(1.5);
    }

    @Test
    void rejectsNonFiniteAndNonNumericDoubleField() {
        DataSchema schema = DataSchema.builder("sensor")
                .field("value", FieldType.DOUBLE)
                .build();

        assertThatThrownBy(() -> DataRecord.single(schema, Map.of("value", "21")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be double");
        assertThatThrownBy(() -> DataRecord.single(schema, Map.of("value", Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be double");
        assertThatThrownBy(() -> DataRecord.single(schema, Map.of("value", Double.POSITIVE_INFINITY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be double");
    }

    @Test
    void storesWholeNumbersInIntegerFieldAsInteger() {
        DataSchema schema = DataSchema.builder("counter")
                .field("value", FieldType.INTEGER)
                .build();

        assertThat(DataRecord.single(schema, Map.of("value", 7)).get("value", 0)).isEqualTo(7);
        assertThat(DataRecord.single(schema, Map.of("value", 7L)).get("value", 0)).isEqualTo(7);
        assertThat(DataRecord.single(schema, Map.of("value", 7.0)).get("value", 0)).isEqualTo(7);
    }

    @Test
    void rejectsFractionAndOutOfRangeIntegerField() {
        DataSchema schema = DataSchema.builder("counter")
                .field("value", FieldType.INTEGER)
                .build();

        assertThatThrownBy(() -> DataRecord.single(schema, Map.of("value", 1.9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be integer");
        assertThatThrownBy(() -> DataRecord.single(schema, Map.of("value", Integer.MAX_VALUE + 1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of integer range");
    }

    @Test
    void parsesDatetimeStringsIntoInstant() {
        DataSchema schema = DataSchema.builder("event")
                .field("at", FieldType.DATETIME)
                .build();

        DataRecord record = DataRecord.single(schema, Map.of("at", "2026-10-01T09:00:00Z"));

        assertThat(record.get("at", 0)).isEqualTo(Instant.parse("2026-10-01T09:00:00Z"));
    }

    @Test
    void rejectsUnparsableDatetimeStrings() {
        DataSchema schema = DataSchema.builder("event")
                .field("at", FieldType.DATETIME)
                .build();

        assertThatThrownBy(() -> DataRecord.single(schema, Map.of("at", "not-a-date")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be datetime");
        assertThatThrownBy(() -> DataRecord.single(schema, Map.of("at", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be datetime");
    }
}
