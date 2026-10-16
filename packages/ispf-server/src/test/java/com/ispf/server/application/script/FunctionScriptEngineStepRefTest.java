package com.ispf.server.application.script;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.ref.PlatformRefParseException;
import com.ispf.server.workflow.WorkflowInstanceCancelService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunctionScriptEngineStepRefTest {

    private static final DataSchema OUT = DataSchema.builder("out")
            .field("error_code", FieldType.STRING)
            .field("error_message", FieldType.STRING)
            .field("matched", FieldType.STRING)
            .build();

    private final PlatformScriptBridge bridge = mock(PlatformScriptBridge.class);
    private final FunctionScriptEngine engine = new FunctionScriptEngine(
            mock(JdbcTemplate.class),
            new ObjectMapper(),
            mock(WorkflowInstanceCancelService.class),
            bridge
    );

    private static final ScriptExecutionContext CONTEXT = new ScriptExecutionContext() {
        @Override
        public String callerObjectPath() {
            return "root.caller";
        }

        @Override
        public com.ispf.core.model.DataRecord invokeFunction(String objectPath, String functionName, java.util.Map<String, Object> input) {
            throw new IllegalStateException("nested invoke");
        }
    };

    @Test
    void unparseableRefIsAnIllegalArgumentNotAnInternalError() {
        String script = """
                {"steps":[
                  {"type":"readVariable","ref":"not a ref","objectPath":"root.pump","variable":"level","var":"got"},
                  {"type":"return","fields":{"error_code":"OK","error_message":"","matched":""}}
                ]}
                """;

        assertThatThrownBy(() -> engine.execute(script, null, OUT, CONTEXT))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(PlatformRefParseException.class)
                .hasMessage("Invalid PlatformRef: not a ref");
    }

    @Test
    void objectPathWithoutRefReadsTheVariable() {
        when(bridge.readVariableField("root.pump", "level", "value")).thenReturn("ok");
        String script = """
                {"steps":[
                  {"type":"readVariable","objectPath":"root.pump","variable":"level","field":"value","var":"got"},
                  {"type":"return","fields":{"error_code":"OK","error_message":"","matched":"${got}"}}
                ]}
                """;

        assertThat(engine.execute(script, null, OUT, CONTEXT).firstRow()).containsEntry("matched", "ok");
        verify(bridge).readVariableField("root.pump", "level", "value");
    }
}
