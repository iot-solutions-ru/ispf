package com.ispf.server.function.java;

import com.ispf.core.function.ObjectJavaFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaFunctionCompilerTest {

  private static final String ECHO_SOURCE = """
      import com.ispf.core.function.ObjectJavaFunction;
      import com.ispf.core.function.JavaFunctionContext;
      import com.ispf.core.model.DataRecord;
      import com.ispf.core.model.DataSchema;
      import com.ispf.core.model.FieldType;
      import java.util.Map;

      public class EchoJavaFn implements ObjectJavaFunction {
          @Override
          public DataRecord invoke(DataRecord input, JavaFunctionContext context) {
              Object value = input != null && input.rowCount() > 0 ? input.firstRow().get("value") : null;
              DataSchema schema = DataSchema.builder("out").field("value", FieldType.STRING).build();
              return DataRecord.single(schema, Map.of("value", value == null ? "" : String.valueOf(value)));
          }
      }
      """;

    @Test
    void compilesAndInstantiatesEchoFunction() {
        JavaFunctionCompiler.CompiledArtifact artifact = JavaFunctionCompiler.compile(ECHO_SOURCE);
        assertEquals("EchoJavaFn", artifact.className());
        ObjectJavaFunction fn = JavaFunctionCompiler.instantiate(artifact);
        assertNotNull(fn.invoke(null, new com.ispf.core.function.JavaFunctionContext("root.test", "echo")));
    }

    @Test
    void rejectsForbiddenConstructs() {
        String source = """
            public class BadFn implements com.ispf.core.function.ObjectJavaFunction {
                public com.ispf.core.model.DataRecord invoke(
                        com.ispf.core.model.DataRecord input,
                        com.ispf.core.function.JavaFunctionContext context) {
                    Runtime.getRuntime();
                    return input;
                }
            }
            """;
        assertThrows(IllegalArgumentException.class, () -> JavaFunctionCompiler.compile(source));
    }
}
