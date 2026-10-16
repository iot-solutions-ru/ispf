package com.ispf.server.application.script;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.workflow.WorkflowInstanceCancelService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FunctionScriptEngineNullEqualsTest {

    private static final DataSchema OUT = DataSchema.builder("out")
            .field("error_code", FieldType.STRING)
            .field("error_message", FieldType.STRING)
            .field("matched", FieldType.STRING)
            .build();

    private final FunctionScriptEngine engine = new FunctionScriptEngine(
            mock(JdbcTemplate.class),
            new ObjectMapper(),
            mock(WorkflowInstanceCancelService.class),
            mock(PlatformScriptBridge.class)
    );

    private static final ScriptExecutionContext CONTEXT = (path, name, input) -> {
        throw new IllegalStateException("nested invoke");
    };

    @Test
    void missingValueIsNotTheTextNull() {
        String script = """
                {"steps":[
                  {"type":"setVar","var":"x","value":null},
                  {"type":"failIfNotEquals","var":"x","equals":"null","error_code":"NE","error_message":"missing is not the text null"},
                  {"type":"return","fields":{"error_code":"OK","error_message":"","matched":"yes"}}
                ]}
                """;

        assertThat(engine.execute(script, null, OUT, CONTEXT).firstRow())
                .containsEntry("error_code", "NE")
                .containsEntry("error_message", "missing is not the text null");
    }

    @Test
    void literalTextNullStillMatches() {
        String script = """
                {"steps":[
                  {"type":"setVar","var":"x","value":"null"},
                  {"type":"when","var":"x","equals":"null","then":[
                    {"type":"return","fields":{"error_code":"OK","error_message":"","matched":"text"}}
                  ]},
                  {"type":"return","fields":{"error_code":"MISS","error_message":"","matched":""}}
                ]}
                """;

        assertThat(engine.execute(script, null, OUT, CONTEXT).firstRow())
                .containsEntry("error_code", "OK")
                .containsEntry("matched", "text");
    }

    @Test
    void notEqualsTreatsMissingAsDifferentFromTextNull() {
        String script = """
                {"steps":[
                  {"type":"setVar","var":"x","value":null},
                  {"type":"when","var":"x","notEquals":"null","then":[
                    {"type":"return","fields":{"error_code":"OK","error_message":"","matched":"different"}}
                  ]},
                  {"type":"return","fields":{"error_code":"MISS","error_message":"","matched":""}}
                ]}
                """;

        assertThat(engine.execute(script, null, OUT, CONTEXT).firstRow())
                .containsEntry("matched", "different");
    }
}
