package com.ispf.server.application.script;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.workflow.WorkflowInstanceCancelService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunctionScriptEnginePathInterpolationTest {

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

    private static final ScriptExecutionContext CONTEXT = (path, name, input) -> {
        throw new IllegalStateException("nested invoke");
    };

    @Test
    void writeVariableInterpolatesObjectPath() {
        String script = """
                {"steps":[
                  {"type":"setVar","var":"room","value":{"object_path":"root.pump"}},
                  {"type":"writeVariable","objectPath":"${room.object_path}","variable":"level","fields":{"value":"hot"}},
                  {"type":"return","fields":{"error_code":"OK","error_message":"","matched":"wrote"}}
                ]}
                """;

        engine.execute(script, null, OUT, CONTEXT);

        verify(bridge).writeVariableFields("root.pump", "level", Map.of("value", "hot"));
    }

    @Test
    void readVariableInterpolatesObjectPath() {
        when(bridge.readVariableField("root.pump", "level", "value")).thenReturn("ok");
        String script = """
                {"steps":[
                  {"type":"setVar","var":"room","value":{"object_path":"root.pump"}},
                  {"type":"readVariable","objectPath":"${room.object_path}","variable":"level","field":"value","var":"got"},
                  {"type":"return","fields":{"error_code":"OK","error_message":"","matched":"${got}"}}
                ]}
                """;

        var row = engine.execute(script, null, OUT, CONTEXT).firstRow();

        verify(bridge).readVariableField("root.pump", "level", "value");
        org.assertj.core.api.Assertions.assertThat(row).containsEntry("matched", "ok");
    }
}
