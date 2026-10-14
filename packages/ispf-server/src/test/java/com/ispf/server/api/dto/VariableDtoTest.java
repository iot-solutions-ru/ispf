package com.ispf.server.api.dto;

import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VariableDtoTest {

    @Test
    void fromExposesDefinitionSchemaWhenLiveValueIsUnset() {
        DataSchema schema = DataSchema.builder("deviceStatus")
                .field("online", FieldType.BOOLEAN)
                .field("value", FieldType.STRING)
                .build();
        Variable variable = new Variable("status", schema, true, true, null);

        VariableDto dto = VariableDto.from(variable);

        assertThat(dto.value()).isNotNull();
        assertThat(dto.value().schema().fields()).extracting("name").containsExactly("online", "value");
        assertThat(dto.value().rowCount()).isZero();
        assertThat(dto.schema().fields()).extracting("name").containsExactly("online", "value");
    }

    @Test
    void fromReplacesEmptyValueSchemaWithDefinition() {
        DataSchema definition = DataSchema.builder("deviceStatus")
                .field("online", FieldType.BOOLEAN)
                .field("value", FieldType.STRING)
                .build();
        DataSchema legacyPayloadSchema = DataSchema.builder("legacy")
                .field("value", FieldType.STRING)
                .build();
        DataRecord stored = DataRecord.single(legacyPayloadSchema, java.util.Map.of("value", "ok"));
        Variable variable = new Variable("status", definition, true, true, stored);

        VariableDto dto = VariableDto.from(variable);

        assertThat(dto.schema().fields()).extracting("name").containsExactly("online", "value");
        assertThat(dto.value().schema().fields()).extracting("name").containsExactly("value");
        assertThat(dto.value().firstRow().get("value")).isEqualTo("ok");
    }
}
