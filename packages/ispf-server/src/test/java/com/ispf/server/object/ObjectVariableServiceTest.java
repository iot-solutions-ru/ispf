package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.function.java.JavaFunctionRuntimeService;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectVariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectVariableServiceTest {

    private static final String PATH = "root.devices.pump-1";
    private static final DataSchema SCHEMA = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .build();

    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectVariableRepository variableRepository;
    @Mock
    private ObjectEntityMapper mapper;
    @Mock
    private DeviceTelemetryPolicyService telemetryPolicyService;
    @Mock
    private JavaFunctionRuntimeService javaFunctionRuntimeService;

    private ObjectTree objectTree;
    private PlatformObject node;
    private ObjectVariableService variableService;

    @BeforeEach
    void setUp() {
        objectTree = new ObjectTree();
        objectTree.register(new PlatformObject("devices", "root.devices", ObjectType.DEVICES, "Devices", "", null));
        node = new PlatformObject("pump", PATH, ObjectType.DEVICE, "Pump", "", null);
        objectTree.register(node);
        when(objectManager.tree()).thenReturn(objectTree);
        variableService = new ObjectVariableService(
                objectManager,
                variableRepository,
                mapper,
                telemetryPolicyService,
                javaFunctionRuntimeService
        );
    }

    @Test
    void createVariablePersistsAndAudits() {
        when(mapper.auditDiff(any(), any())).thenReturn("{}");

        Variable created = variableService.createVariable(
                PATH,
                "temperature",
                SCHEMA,
                true,
                true,
                DataRecord.single(SCHEMA, Map.of("value", 1.0)),
                false,
                null
        );

        assertThat(created.name()).isEqualTo("temperature");
        assertThat(node.getVariable("temperature")).isPresent();
        verify(objectManager).persistVariable(eq(PATH), any(Variable.class));
        verify(objectManager).bumpRevision(node);
        verify(objectManager).recordAudit(eq(PATH), eq("CREATE_VARIABLE"), eq("temperature"), any(Long.class), any(Long.class), any());
    }

    @Test
    void createVariableRejectsDuplicate() {
        node.addVariable(new Variable("temperature", SCHEMA, true, true, null));

        assertThatThrownBy(() -> variableService.createVariable(
                PATH,
                "temperature",
                SCHEMA,
                true,
                true,
                null,
                false,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void setVariableValueUpdatesAndPublishes() {
        node.addVariable(new Variable("temperature", SCHEMA, true, true, null));
        when(mapper.auditDiff(any(), any())).thenReturn("{}");
        DataRecord value = DataRecord.single(SCHEMA, Map.of("value", 42.0));

        Variable updated = variableService.setVariableValue(PATH, "temperature", value);

        assertThat(updated.value()).contains(value);
        verify(objectManager).persistVariable(PATH, updated);
        verify(objectManager).bumpRevision(node);
        ArgumentCaptor<ObjectChangeEvent> eventCaptor = ArgumentCaptor.forClass(ObjectChangeEvent.class);
        verify(objectManager).publishConfigChange(eventCaptor.capture(), eq(node));
        assertThat(eventCaptor.getValue().type()).isEqualTo(ObjectChangeType.VARIABLE_UPDATED);
    }

    @Test
    void upsertFunctionSyncsRuntimeAndPersistsConfig() {
        FunctionDescriptor function = new FunctionDescriptor(
                "ping",
                "Ping",
                null,
                null,
                "expression",
                "1+1",
                null,
                null
        );
        when(mapper.auditDiff(any(), any())).thenReturn("{}");

        FunctionDescriptor saved = variableService.upsertFunction(PATH, function);

        assertThat(saved.name()).isEqualTo("ping");
        verify(javaFunctionRuntimeService).syncOnSave(PATH, function, null);
        verify(objectManager).persistNodeConfig(eq(node), eq("UPSERT_FUNCTION"), eq("ping"), any());
        verify(objectManager).publishConfigChange(eq(ObjectChangeType.UPDATED), eq(PATH), any(Long.class));
    }

    @Test
    void updateVariableHistoryInvalidatesTelemetryPolicy() {
        node.addVariable(new Variable("temperature", SCHEMA, true, true, null, true, 7));
        when(mapper.auditDiff(any(), any())).thenReturn("{}");

        Variable updated = variableService.updateVariableHistory(
                PATH,
                "temperature",
                false,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(updated.historyEnabled()).isFalse();
        verify(telemetryPolicyService).invalidateVariable(PATH, "temperature");
        verify(objectManager).persistVariable(eq(PATH), any(Variable.class));
    }
}
