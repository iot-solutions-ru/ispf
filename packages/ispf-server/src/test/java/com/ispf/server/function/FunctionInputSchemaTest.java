package com.ispf.server.function;

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

class FunctionInputSchemaTest {

    @Test
    void ignoresClientSchemaAndKeepsContractFields() {
        DataSchema contract = DataSchema.builder("in")
                .field("count", FieldType.INTEGER)
                .field("flag", FieldType.BOOLEAN)
                .build();
        DataSchema client = DataSchema.builder("wf")
                .field("count", FieldType.STRING)
                .field("extra", FieldType.STRING)
                .build();
        DataRecordPayloadRequest payload = new DataRecordPayloadRequest(
                client,
                List.of(Map.of("count", "7", "extra", "x", "flag", "true"))
        );

        DataRecord resolved = FunctionInputSchema.apply(contract, payload);

        assertThat(resolved.schema()).isEqualTo(contract);
        assertThat(resolved.firstRow().get("count")).isEqualTo(7);
        assertThat(resolved.firstRow().get("flag")).isEqualTo(true);
        assertThat(resolved.firstRow()).doesNotContainKey("extra");
    }

    @Test
    void emptyBodyFailsOnRequiredInputField() {
        DataSchema contract = DataSchema.builder("in")
                .field(FieldDefinition.required("jobNo", FieldType.STRING))
                .build();

        assertThatThrownBy(() -> FunctionInputSchema.apply(contract, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobNo");
    }

    @Test
    void voidInputStaysEmpty() {
        DataSchema voidInput = DataSchema.builder("voidInput").build();

        assertThat(FunctionInputSchema.apply(voidInput, null).rowCount()).isZero();
    }
}
