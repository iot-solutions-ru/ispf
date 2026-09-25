package com.ispf.server.api.dto;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataRecordPayloadResolverTest {

    private static final DataSchema DEFAULT = DataSchema.builder("in")
            .field("jobNo", FieldType.STRING)
            .build();

    @Test
    void usesDescriptorSchemaWhenClientSchemaHasNoFields() {
        DataRecordPayloadRequest payload = new DataRecordPayloadRequest(
                DataSchema.builder("functionInput").build(),
                List.of(Map.of("jobNo", "PRINT-2026-001"))
        );

        DataRecord resolved = DataRecordPayloadResolver.resolve(DEFAULT, payload);

        assertThat(resolved.firstRow().get("jobNo")).isEqualTo("PRINT-2026-001");
    }

    @Test
    void projectsOntoContractSchemaWhenClientSchemaHasFields() {
        DataSchema client = DataSchema.builder("custom")
                .field("action", FieldType.STRING)
                .build();
        DataRecordPayloadRequest payload = new DataRecordPayloadRequest(
                client,
                List.of(Map.of("action", "open", "jobNo", "PRINT-1"))
        );

        DataRecord resolved = DataRecordPayloadResolver.resolve(DEFAULT, payload);

        assertThat(resolved.schema().name()).isEqualTo("in");
        assertThat(resolved.firstRow().get("jobNo")).isEqualTo("PRINT-1");
        assertThat(resolved.firstRow()).doesNotContainKey("action");
    }

    @Test
    void emptyBodyFailsWhenContractRequiresFields() {
        DataSchema required = DataSchema.builder("in")
                .field(FieldDefinition.required("jobNo", FieldType.STRING))
                .build();

        assertThatThrownBy(() -> DataRecordPayloadResolver.resolve(required, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobNo");
    }
}
