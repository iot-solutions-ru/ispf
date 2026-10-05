package com.ispf.server.event;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventPayloadSchemaTest {

    @Test
    void ignoresClientSchemaDropsExtrasAndCoercesContractFields() {
        DataSchema contract = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .field("unit", FieldType.STRING)
                .build();
        DataSchema client = DataSchema.builder("custom")
                .field("action", FieldType.STRING)
                .field("value", FieldType.STRING)
                .build();
        DataRecordPayloadRequest payload = new DataRecordPayloadRequest(
                client,
                List.of(Map.of("action", "open", "value", "41.5", "unit", "C"))
        );

        DataRecord resolved = EventPayloadSchema.apply(contract, payload);

        assertThat(resolved.schema()).isEqualTo(contract);
        assertThat(resolved.firstRow().get("value")).isEqualTo(41.5);
        assertThat(resolved.firstRow().get("unit")).isEqualTo("C");
        assertThat(resolved.firstRow()).doesNotContainKey("action");
    }

    @Test
    void emptyBodyWithOnlyOptionalFieldsStaysEmptyOnTheEventSchema() {
        DataSchema contract = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .build();

        DataRecord resolved = EventPayloadSchema.apply(contract, null);

        assertThat(resolved.schema()).isEqualTo(contract);
        assertThat(resolved.rowCount()).isZero();
    }

    @Test
    void missingRequiredFieldIsACallError() {
        DataSchema contract = DataSchema.builder("alarm")
                .field(FieldDefinition.required("code", FieldType.STRING))
                .field("note", FieldType.STRING)
                .build();
        DataRecordPayloadRequest payload = new DataRecordPayloadRequest(
                DataSchema.builder("custom").field("note", FieldType.STRING).build(),
                List.of(Map.of("note", "late", "extra", "x"))
        );

        assertThatThrownBy(() -> EventPayloadSchema.apply(contract, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    void emptyBodyWithRequiredFieldIsACallError() {
        DataSchema contract = DataSchema.builder("alarm")
                .field(FieldDefinition.required("code", FieldType.STRING))
                .build();

        assertThatThrownBy(() -> EventPayloadSchema.apply(contract, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }
}
