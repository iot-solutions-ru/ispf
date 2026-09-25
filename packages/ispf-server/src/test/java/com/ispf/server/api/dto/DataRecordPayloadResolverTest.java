package com.ispf.server.api.dto;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
    void keepsClientSchemaWhenFieldsArePresent() {
        DataSchema client = DataSchema.builder("custom")
                .field("action", FieldType.STRING)
                .build();
        DataRecordPayloadRequest payload = new DataRecordPayloadRequest(
                client,
                List.of(Map.of("action", "open"))
        );

        DataRecord resolved = DataRecordPayloadResolver.resolve(DEFAULT, payload);

        assertThat(resolved.firstRow().get("action")).isEqualTo("open");
        assertThat(resolved.firstRow()).doesNotContainKey("jobNo");
    }
}
