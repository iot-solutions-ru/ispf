package com.ispf.server.application.test;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionRuntime;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.script.PlatformScriptBridge;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FunctionTestRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ApplicationFunctionRuntime functionRuntime;
    @Mock
    private ApplicationFunctionStore functionStore;
    @Mock
    private ApplicationDataStore dataStore;
    @Mock
    private ApplicationSchemaSession schemaSession;
    @Mock
    private PlatformScriptBridge platformScriptBridge;
    @Mock
    private ObjectManager objectManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void functionTestUsesInvokeInCurrentTransactionAndPassesOnExpect() throws Exception {
        DataSchema inputSchema = DataSchema.builder("in").build();
        DataSchema outputSchema = DataSchema.builder("out")
                .field("error_code", FieldType.STRING)
                .build();
        ApplicationFunctionHandler.DeployedFunction deployed = new ApplicationFunctionHandler.DeployedFunction(
                UUID.randomUUID(),
                "demo",
                "root.platform.singleton-blueprints.demo-app-hub-v1",
                "demo_ping",
                "1",
                "script",
                "{}",
                objectMapper.writeValueAsString(inputSchema),
                objectMapper.writeValueAsString(outputSchema)
        );
        when(functionStore.findLatest(anyString(), anyString())).thenReturn(Optional.of(deployed));
        when(functionRuntime.invokeInCurrentTransaction(anyString(), anyString(), any()))
                .thenReturn(DataRecord.single(outputSchema, Map.of("error_code", "OK")));

        FunctionTestRunner runner = new FunctionTestRunner(
                jdbcTemplate,
                functionRuntime,
                functionStore,
                dataStore,
                schemaSession,
                platformScriptBridge,
                objectManager,
                objectMapper
        );

        boolean syncActive = TransactionSynchronizationManager.isSynchronizationActive();
        if (!syncActive) {
            TransactionSynchronizationManager.initSynchronization();
        }
        try {
            FunctionTestRunner.TestResult result = runner.run(new FunctionTestRunner.TestSpec(
                    "demo-ping",
                    "function",
                    "root.platform.singleton-blueprints.demo-app-hub-v1",
                    "demo_ping",
                    null,
                    Map.of(),
                    List.of(),
                    Map.of(),
                    Map.of("errorCode", "OK", "rowCount", 1),
                    true
            ));

            assertThat(result.status()).isEqualTo("PASS");
            ArgumentCaptor<DataRecord> inputCaptor = ArgumentCaptor.forClass(DataRecord.class);
            verify(functionRuntime).invokeInCurrentTransaction(
                    eq("root.platform.singleton-blueprints.demo-app-hub-v1"),
                    eq("demo_ping"),
                    inputCaptor.capture()
            );
        } finally {
            if (!syncActive) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    void telemetryTestWritesViaSetDriverTelemetry() {
        FunctionTestRunner runner = new FunctionTestRunner(
                jdbcTemplate,
                functionRuntime,
                functionStore,
                dataStore,
                schemaSession,
                platformScriptBridge,
                objectManager,
                objectMapper
        );

        com.ispf.core.object.PlatformObject node = org.mockito.Mockito.mock(com.ispf.core.object.PlatformObject.class);
        when(objectManager.require(anyString())).thenReturn(node);
        when(node.getVariable(anyString())).thenReturn(Optional.empty());

        boolean syncActive = TransactionSynchronizationManager.isSynchronizationActive();
        if (!syncActive) {
            TransactionSynchronizationManager.initSynchronization();
        }
        try {
            FunctionTestRunner.TestResult result = runner.run(new FunctionTestRunner.TestSpec(
                    "temp-write",
                    "telemetry",
                    "root.platform.devices.sensor-1",
                    null,
                    "temperature",
                    Map.of(),
                    List.of(),
                    Map.of("value", 21.5, "unit", "C"),
                    Map.of(),
                    true
            ));
            verify(platformScriptBridge).setDriverTelemetry(
                    eq("root.platform.devices.sensor-1"),
                    eq("temperature"),
                    eq(Map.of("value", 21.5, "unit", "C"))
            );
            assertThat(result.status()).isEqualTo("PASS");
        } finally {
            if (!syncActive) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }
}
