package com.ispf.server.function.java;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.function.FunctionService;
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
class JavaFunctionRuntimeIntegrationTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String SOURCE = """
            import com.ispf.core.function.ObjectJavaFunction;
            import com.ispf.core.function.JavaFunctionContext;
            import com.ispf.core.model.DataRecord;
            import com.ispf.core.model.DataSchema;
            import com.ispf.core.model.FieldType;
            import java.util.Map;

            public class EchoInputFn implements ObjectJavaFunction {
                @Override
                public DataRecord invoke(DataRecord input, JavaFunctionContext context) {
                    Object value = input != null && input.rowCount() > 0 ? input.firstRow().get("value") : null;
                    DataSchema schema = DataSchema.builder("out").field("value", FieldType.STRING).build();
                    return DataRecord.single(schema, Map.of("value", "java:" + value));
                }
            }
            """;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private FunctionService functionService;

    private String functionName;

    @AfterEach
    void cleanup() {
        if (functionName != null) {
            objectManager.deleteFunction(DEVICE, functionName);
            functionName = null;
        }
    }

    @Test
    void upsertCompilesJavaFunctionAndInvokeReturnsOutput() {
        functionName = "javaEcho" + System.nanoTime();
        DataSchema inputSchema = DataSchema.builder("in").field("value", FieldType.STRING).build();
        DataSchema outputSchema = DataSchema.builder("out").field("value", FieldType.STRING).build();
        objectManager.upsertFunction(DEVICE, new FunctionDescriptor(
                functionName,
                "Java echo",
                inputSchema,
                outputSchema,
                "java",
                SOURCE,
                null,
                "1"
        ));

        DataRecord result = functionService.invoke(
                DEVICE,
                functionName,
                DataRecord.single(inputSchema, Map.of("value", "42"))
        );

        assertThat(result.firstRow().get("value")).isEqualTo("java:42");
    }
}
