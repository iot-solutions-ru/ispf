package com.ispf.core.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataRecordProjectionTest {

    @Test
    void extraQueryColumnsAreDroppedOntoTheVariableSchema() {
        DataSchema variable = DataSchema.builder("families")
                .field("id", FieldType.STRING)
                .field("family", FieldType.STRING)
                .build();
        DataSchema query = DataSchema.builder("jdbcRow")
                .field("id", FieldType.STRING)
                .field("family", FieldType.STRING)
                .field("description", FieldType.STRING)
                .build();
        DataRecord incoming = DataRecord.single(query, Map.of(
                "id", "1",
                "family", "01001",
                "description", "PAP Balkan Star"
        ));

        DataRecord stored = incoming.projectedOnto(variable);

        assertThat(stored.schema().fields()).extracting(FieldDefinition::name)
                .containsExactly("id", "family");
        assertThat(stored.firstRow())
                .containsEntry("id", "1")
                .containsEntry("family", "01001")
                .doesNotContainKey("description");
    }

    @Test
    void unrelatedPayloadIsLeftIntact() {
        DataSchema variable = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .field("unit", FieldType.STRING)
                .build();
        DataSchema payload = DataSchema.builder("mqttPayload")
                .field("raw", FieldType.STRING)
                .build();
        DataRecord incoming = DataRecord.single(payload, Map.of("raw", "99.9"));

        assertThat(incoming.projectedOnto(variable)).isSameAs(incoming);
    }

    @Test
    void variableFieldMissingFromTheReadBecomesEmpty() {
        DataSchema variable = DataSchema.builder("families")
                .field("id", FieldType.STRING)
                .field("note", FieldType.STRING)
                .build();
        DataSchema query = DataSchema.builder("jdbcRow")
                .field("id", FieldType.STRING)
                .field("description", FieldType.STRING)
                .build();
        DataRecord incoming = DataRecord.single(query, Map.of("id", "1", "description", "gone"));

        DataRecord stored = incoming.projectedOnto(variable);

        assertThat(stored.firstRow())
                .containsEntry("id", "1")
                .containsEntry("note", null)
                .doesNotContainKey("description");
    }
}
