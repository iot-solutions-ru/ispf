package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionEngineInputBindingTest {

    @Test
    void evaluatesInputAndContextBindings() {
        ExpressionEngine engine = new ExpressionEngine();
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.test",
                ObjectType.DEVICE,
                "test",
                "",
                null
        );
        Map<String, Object> input = Map.of("value", 21.0);

        assertThat(engine.evaluate("input.value * 2.0", node, input)).isEqualTo(42.0);
        assertThat(engine.evaluate("context.value + 1.0", node, input)).isEqualTo(22.0);
    }

    @Test
    void bindingEvaluatorMapsFunctionOutput() {
        BindingExpressionEvaluator evaluator = new BindingExpressionEvaluator();
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.test",
                ObjectType.DEVICE,
                "test",
                "",
                null
        );
        DataSchema output = DataSchema.builder("out").field("result", FieldType.DOUBLE).build();
        DataRecord input = DataRecord.single(
                DataSchema.builder("in").field("value", FieldType.DOUBLE).build(),
                Map.of("value", 9.0)
        );

        var result = evaluator.evaluate(
                node,
                "_fn",
                "input.value + 1.0",
                output,
                (objectPath, functionName, record) -> java.util.Optional.empty(),
                input.firstRow()
        );

        assertThat(result).isPresent();
        assertThat(result.get().firstRow().get("result")).isEqualTo(10.0);
    }
}
