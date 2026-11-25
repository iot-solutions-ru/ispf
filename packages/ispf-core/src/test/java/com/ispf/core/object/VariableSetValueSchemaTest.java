package com.ispf.core.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableSetValueSchemaTest {

    @Test
    void rejectsValueWhoseSchemaDoesNotMatch() {
        DataSchema integer = DataSchema.builder("count").field("value", FieldType.INTEGER).build();
        Variable variable = new Variable(
                "count",
                integer,
                true,
                true,
                DataRecord.single(integer, Map.of("value", 1))
        );
        DataSchema text = DataSchema.builder("count").field("value", FieldType.STRING).build();
        DataSchema fractional = DataSchema.builder("count").field("value", FieldType.DOUBLE).build();

        assertThatThrownBy(() -> variable.setValue(DataRecord.single(text, Map.of("value", "abc"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("value schema is not compatible with variable count");
        assertThatThrownBy(() -> variable.setValue(DataRecord.single(fractional, Map.of("value", 1.9))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("value schema is not compatible with variable count");

        assertThat(variable.schema().field("value").orElseThrow().type()).isEqualTo(FieldType.INTEGER);
        assertThat(variable.value().orElseThrow().schema().field("value").orElseThrow().type()).isEqualTo(FieldType.INTEGER);
        assertThat(variable.value().orElseThrow().firstRow().get("value")).isEqualTo(1);
    }

    @Test
    void acceptsValueWithTheDeclaredSchema() {
        DataSchema integer = DataSchema.builder("count").field("value", FieldType.INTEGER).build();
        Variable variable = new Variable(
                "count",
                integer,
                true,
                true,
                DataRecord.single(integer, Map.of("value", 1))
        );

        variable.setValue(DataRecord.single(integer, Map.of("value", 2)));

        assertThat(variable.value().orElseThrow().schema().field("value").orElseThrow().type()).isEqualTo(FieldType.INTEGER);
        assertThat(variable.value().orElseThrow().firstRow().get("value")).isEqualTo(2);
    }
}
