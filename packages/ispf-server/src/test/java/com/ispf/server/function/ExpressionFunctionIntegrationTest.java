package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExpressionFunctionIntegrationTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private ExpressionFunctionHandler expressionFunctionHandler;

    @Autowired
    private ScriptFunctionHandler scriptFunctionHandler;

    private String functionName;

    @AfterEach
    void cleanup() {
        if (functionName != null) {
            objectManager.deleteFunction(DEVICE, functionName);
            functionName = null;
        }
    }

    @Test
    void invokeEvaluatesCelWithInputVariable() {
        functionName = "exprInput" + System.nanoTime();
        DataSchema inputSchema = DataSchema.builder("in").field("value", FieldType.DOUBLE).build();
        DataSchema outputSchema = DataSchema.builder("out").field("result", FieldType.DOUBLE).build();
        objectManager.upsertFunction(DEVICE, new FunctionDescriptor(
                functionName,
                "Double input via CEL input.*",
                inputSchema,
                outputSchema,
                "expression",
                "input.value * 2.0",
                null,
                null
        ));

        assertThat(expressionFunctionHandler.supports(DEVICE, functionName)).isTrue();
        assertThat(scriptFunctionHandler.supports(DEVICE, functionName)).isFalse();

        DataRecord result = functionService.invoke(
                DEVICE,
                functionName,
                DataRecord.single(inputSchema, Map.of("value", 21.0))
        );

        assertThat(result.firstRow().get("result")).isEqualTo(42.0);
    }

    @Test
    void invokeEvaluatesCelWithContextVariable() {
        functionName = "exprContext" + System.nanoTime();
        DataSchema inputSchema = DataSchema.builder("in").field("value", FieldType.DOUBLE).build();
        DataSchema outputSchema = DataSchema.builder("out").field("result", FieldType.DOUBLE).build();
        objectManager.upsertFunction(DEVICE, new FunctionDescriptor(
                functionName,
                "Double input via CEL context.*",
                inputSchema,
                outputSchema,
                "expression",
                "context.value + 1.0",
                null,
                null
        ));

        DataRecord result = functionService.invoke(
                DEVICE,
                functionName,
                DataRecord.single(inputSchema, Map.of("value", 9.0))
        );

        assertThat(result.firstRow().get("result")).isEqualTo(10.0);
    }
}
