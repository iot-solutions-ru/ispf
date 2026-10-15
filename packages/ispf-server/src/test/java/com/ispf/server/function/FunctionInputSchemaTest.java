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
    void emptyBodyRejectsMissingRequiredField() {
        DataSchema contract = DataSchema.builder("in")
                .field(FieldDefinition.required("jobNo", FieldType.STRING))
                .field("finishCode", FieldType.STRING)
                .build();

        assertThatThrownBy(() -> FunctionInputSchema.apply(contract, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Required field missing: jobNo");
    }

    @Test
    void emptyRowRejectsMissingRequiredIntegerField() {
        DataSchema contract = DataSchema.builder("in")
                .field(FieldDefinition.required("req", FieldType.INTEGER))
                .build();
        DataRecordPayloadRequest payload = new DataRecordPayloadRequest(
                null,
                List.of(Map.of())
        );

        assertThatThrownBy(() -> FunctionInputSchema.apply(contract, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Required field missing: req");
    }

    @Test
    void optionalFieldsFilledWhenRequiredPresent() {
        DataSchema contract = DataSchema.builder("in")
                .field(FieldDefinition.required("jobNo", FieldType.STRING))
                .field("finishCode", FieldType.STRING)
                .build();
        DataRecordPayloadRequest payload = new DataRecordPayloadRequest(
                null,
                List.of(Map.of("jobNo", "J-1"))
        );

        DataRecord resolved = FunctionInputSchema.apply(contract, payload);

        assertThat(resolved.firstRow().get("jobNo")).isEqualTo("J-1");
        assertThat(resolved.firstRow().get("finishCode")).isNull();
    }

    @Test
    void voidInputStaysEmpty() {
        DataSchema voidInput = DataSchema.builder("voidInput").build();

        assertThat(FunctionInputSchema.apply(voidInput, null).rowCount()).isZero();
    }
}
