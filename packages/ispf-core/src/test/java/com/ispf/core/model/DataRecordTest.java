package com.ispf.core.model;

import org.junit.jupiter.api.Test;

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
}
