package com.ispf.core.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataRecordValuesTest {

    @Test
    void equalTreatsBinaryPayloadsByContent() {
        DataSchema schema = DataSchema.builder("blob")
                .field("payload", FieldType.BINARY)
                .build();
        byte[] leftBytes = new byte[] {1, 2, 3};
        byte[] rightBytes = new byte[] {1, 2, 3};
        DataRecord left = DataRecord.single(schema, Map.of("payload", leftBytes));
        DataRecord right = DataRecord.single(schema, Map.of("payload", rightBytes));

        assertThat(leftBytes).isNotSameAs(rightBytes);
        assertThat(DataRecordValues.equal(left, right)).isTrue();
        assertThat(DataRecordValues.equal(
                left,
                DataRecord.single(schema, Map.of("payload", new byte[] {9}))
        )).isFalse();
    }

    @Test
    void equalTreatsCoercedLongValuesAsEqual() {
        DataSchema schema = DataSchema.builder("counter")
                .field("value", FieldType.LONG)
                .build();
        DataRecord fromInt = DataRecord.single(schema, Map.of("value", 7));
        DataRecord fromLong = DataRecord.single(schema, Map.of("value", 7L));
        assertThat(DataRecordValues.equal(fromInt, fromLong)).isTrue();
    }
}
