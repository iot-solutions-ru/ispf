package com.ispf.server.function.java;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "ispf.function.java.enabled=true")
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

    @Test
    void invokeDropsFieldsOutsideOutputSchema() {
        functionName = "javaExtra" + System.nanoTime();
        DataSchema inputSchema = DataSchema.builder("in").field("value", FieldType.STRING).build();
        DataSchema outputSchema = DataSchema.builder("out").field("value", FieldType.STRING).build();
        objectManager.upsertFunction(DEVICE, new FunctionDescriptor(
                functionName,
                "Java extra field",
                inputSchema,
                outputSchema,
                "java",
                extraFieldSource(),
                null,
                "1"
        ));

        DataRecord result = functionService.invoke(
                DEVICE,
                functionName,
                DataRecord.single(inputSchema, Map.of("value", "x"))
        );

        assertThat(result.schema().fields()).extracting("name").containsExactly("value");
        assertThat(result.firstRow()).containsOnlyKeys("value");
        assertThat(result.firstRow().get("value")).isEqualTo("x");
    }

    @Test
    void invokeFailsWhenRequiredOutputFieldIsMissing() {
        functionName = "javaMissing" + System.nanoTime();
        DataSchema inputSchema = DataSchema.builder("in").field("value", FieldType.STRING).build();
        DataSchema outputSchema = DataSchema.builder("out")
                .field(FieldDefinition.required("value", FieldType.STRING))
                .build();
        objectManager.upsertFunction(DEVICE, new FunctionDescriptor(
                functionName,
                "Java missing required",
                inputSchema,
                outputSchema,
                "java",
                missingFieldSource(),
                null,
                "1"
        ));

        assertThatThrownBy(() -> functionService.invoke(
                DEVICE,
                functionName,
                DataRecord.single(inputSchema, Map.of("value", "x"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required field missing: value");
    }

    @Test
    void invokeCoercesNumberToDoubleOutputField() {
        functionName = "javaDouble" + System.nanoTime();
        DataSchema inputSchema = DataSchema.builder("in").field("value", FieldType.STRING).build();
        DataSchema outputSchema = DataSchema.builder("out")
                .field(FieldDefinition.required("value", FieldType.DOUBLE))
                .build();
        objectManager.upsertFunction(DEVICE, new FunctionDescriptor(
                functionName,
                "Java long as double",
                inputSchema,
                outputSchema,
                "java",
                longAsDoubleSource(),
                null,
                "1"
        ));

        DataRecord result = functionService.invoke(
                DEVICE,
                functionName,
                DataRecord.single(inputSchema, Map.of("value", "x"))
        );

        assertThat(result.firstRow().get("value")).isEqualTo(2147483648.0);
    }

    private static String extraFieldSource() {
        return """
                import com.ispf.core.function.ObjectJavaFunction;
                import com.ispf.core.function.JavaFunctionContext;
                import com.ispf.core.model.DataRecord;
                import com.ispf.core.model.DataSchema;
                import com.ispf.core.model.FieldType;
                import java.util.Map;

                public class ExtraFieldFn implements ObjectJavaFunction {
                    @Override
                    public DataRecord invoke(DataRecord input, JavaFunctionContext context) {
                        DataSchema schema = DataSchema.builder("out")
                                .field("value", FieldType.STRING)
                                .field("secret", FieldType.STRING)
                                .build();
                        Object value = input.firstRow().get("value");
                        return DataRecord.single(schema, Map.of("value", value, "secret", "hidden"));
                    }
                }
                """;
    }

    private static String missingFieldSource() {
        return """
                import com.ispf.core.function.ObjectJavaFunction;
                import com.ispf.core.function.JavaFunctionContext;
                import com.ispf.core.model.DataRecord;
                import com.ispf.core.model.DataSchema;
                import com.ispf.core.model.FieldType;
                import java.util.Map;

                public class MissingFieldFn implements ObjectJavaFunction {
                    @Override
                    public DataRecord invoke(DataRecord input, JavaFunctionContext context) {
                        DataSchema schema = DataSchema.builder("out").field("other", FieldType.STRING).build();
                        return DataRecord.single(schema, Map.of("other", "nope"));
                    }
                }
                """;
    }

    private static String longAsDoubleSource() {
        return """
                import com.ispf.core.function.ObjectJavaFunction;
                import com.ispf.core.function.JavaFunctionContext;
                import com.ispf.core.model.DataRecord;
                import com.ispf.core.model.DataSchema;
                import com.ispf.core.model.FieldType;
                import java.util.Map;

                public class LongAsDoubleFn implements ObjectJavaFunction {
                    @Override
                    public DataRecord invoke(DataRecord input, JavaFunctionContext context) {
                        DataSchema schema = DataSchema.builder("out").field("value", FieldType.LONG).build();
                        return DataRecord.single(schema, Map.of("value", 2147483648L));
                    }
                }
                """;
    }
}
