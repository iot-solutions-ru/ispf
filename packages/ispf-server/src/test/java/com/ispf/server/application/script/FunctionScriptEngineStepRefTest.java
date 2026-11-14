package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.ref.PlatformRefParseException;
import com.ispf.server.application.data.ApplicationMessageRelay;
import com.ispf.server.audit.AuditEventService;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import com.ispf.server.workflow.WorkflowInstanceCancelService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void failedAuditLeavesTheSingleOutboxRow() {
        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
        relay.ensureTables();
        AuditEventService audit = mock(AuditEventService.class);
        doThrow(new RuntimeException("audit down")).when(audit)
                .logInNewTransaction(any(), any(), any(), any(), any(), any());
        FunctionScriptEngine outboxEngine = new FunctionScriptEngine(
                jdbc,
                new ObjectMapper(),
                mock(WorkflowInstanceCancelService.class),
                mock(PlatformScriptBridge.class),
                relay,
                audit,
                mock(TenantLocalDataAccessGuard.class)
        );
        DataSchema schema = DataSchema.builder("out")
                .field("error_code", FieldType.STRING)
                .field("error_message", FieldType.STRING)
                .field("enqueued", FieldType.BOOLEAN)
                .build();
        String script = """
                {"steps":[
                  {"type":"enqueueOutbox","connectorId":"erp","messageId":"K-1","body":"{}"},
                  {"type":"return","fields":{"error_code":"OK","error_message":"","enqueued":true}}
                ]}
                """;
        ScriptExecutionContext context = new ScriptExecutionContext() {
            @Override
            public String appId() {
                return "plant-discrete";
            }

            @Override
            public DataRecord invokeFunction(String objectPath, String functionName, Map<String, Object> input) {
                throw new IllegalStateException("nested invoke");
            }
        };
        outboxEngine.execute(script, null, schema, context);
        outboxEngine.execute(script, null, schema, context);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.application_outbox WHERE message_id = ?",
                Integer.class,
                "K-1"
        );
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void enqueueOutboxRejectsACrossAppId() {
        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build());
        ApplicationMessageRelay relay = new ApplicationMessageRelay(jdbc);
        relay.ensureTables();
        FunctionScriptEngine outboxEngine = new FunctionScriptEngine(
                jdbc,
                new ObjectMapper(),
                mock(WorkflowInstanceCancelService.class),
                mock(PlatformScriptBridge.class),
                relay,
                mock(AuditEventService.class),
                mock(TenantLocalDataAccessGuard.class)
        );
        DataSchema schema = DataSchema.builder("out")
                .field("error_code", FieldType.STRING)
                .field("error_message", FieldType.STRING)
                .build();
        String script = """
                {"steps":[
                  {"type":"enqueueOutbox","appId":"other-app","connectorId":"erp","messageId":"K-x","body":"{}"},
                  {"type":"return","fields":{"error_code":"OK","error_message":""}}
                ]}
                """;
        ScriptExecutionContext context = new ScriptExecutionContext() {
            @Override
            public String appId() {
                return "plant-discrete";
            }

            @Override
            public DataRecord invokeFunction(String objectPath, String functionName, Map<String, Object> input) {
                throw new IllegalStateException("nested invoke");
            }
        };
        assertThatThrownBy(() -> outboxEngine.execute(script, null, schema, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match caller appId");
    }
}
