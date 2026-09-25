package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BindingExpressionErrorPropagatesTest {

    private final BindingExpressionEvaluator evaluator = new BindingExpressionEvaluator();

    @Test
    void unknownNameFailsWithExpressionTextInsteadOfEmpty() {
        DataSchema schema = DataSchema.builder("flag").field("value", FieldType.DOUBLE).build();
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.devices.sensor",
                ObjectType.DEVICE,
                "sensor",
                "",
                null
        );
        node.addVariable(new Variable(
                "flag",
                schema,
                true,
                false,
                DataRecord.single(schema, Map.of("value", 0.0))
        ));

        assertThatThrownBy(() -> evaluator.evaluate(
                node,
                "flag",
                "no_such_name",
                schema,
                BindingEvaluationContext.NONE
        ))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("Binding expression failed")
                .hasMessageContaining("no_such_name");
    }

    @Test
    void blankExpressionStillReturnsEmpty() {
        DataSchema schema = DataSchema.builder("flag").field("value", FieldType.DOUBLE).build();
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.devices.sensor",
                ObjectType.DEVICE,
                "sensor",
                "",
                null
        );

        Optional<DataRecord> result = evaluator.evaluate(
                node,
                "flag",
                "  ",
                schema,
                BindingEvaluationContext.NONE
        );
        assertThat(result).isEmpty();
    }
}
