package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.server.application.script.FunctionScriptValidator;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
        lenient().when(objectManager.tree()).thenReturn(objectTree);
        variableService = new ObjectVariableService(
                objectManager,
                variableRepository,
                mapper,
                telemetryPolicyService,
                javaFunctionRuntimeService,
                new FunctionScriptValidator(new ObjectMapper()),
                null
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
    void deleteVariableRejectsBlueprintCatalogMetadata() {
        DataSchema meta = DataSchema.builder("blueprintType").field("value", FieldType.STRING).build();
        node.addVariable(new Variable("blueprintType", meta, true, false, null));
        node.addVariable(new Variable("suitabilityExpression", meta, true, true, null));

        assertThatThrownBy(() -> variableService.deleteVariable(PATH, "blueprintType"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blueprintType");
        assertThatThrownBy(() -> variableService.deleteVariable(PATH, "suitabilityExpression"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suitabilityExpression");
        assertThat(node.getVariable("blueprintType")).isPresent();
        assertThat(node.getVariable("suitabilityExpression")).isPresent();
        verify(variableRepository, never()).deleteByObjectPathAndName(any(), any());
    }

    @Test
    void deleteVariableRejectsIntrinsicCatalogSchemaAndKeepsLocalVariables() {
        DataSchema schema = DataSchema.builder("layout").field("value", FieldType.STRING).build();
        PlatformObject dashboard = new PlatformObject(
                "main",
                "root.platform.dashboards.main",
                ObjectType.DASHBOARD,
                "Main",
                "",
                null
        );
        objectTree.register(dashboard);
        dashboard.addVariable(new Variable("layout", schema, true, true, null));
        dashboard.addVariable(new Variable("note", schema, true, true, null));
        ObjectVariableService scoped = serviceWith(dashboardSchema(schema));
        when(mapper.auditDiff(any(), any())).thenReturn("{}");

        assertThatThrownBy(() -> scoped.deleteVariable(dashboard.path(), "layout"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("layout");
        assertThat(dashboard.getVariable("layout")).isPresent();

        scoped.deleteVariable(dashboard.path(), "note");
        assertThat(dashboard.getVariable("note")).isEmpty();
        verify(variableRepository).deleteByObjectPathAndName(dashboard.path(), "note");
    }

    @Test
    void deleteVariableAllowsSchemaNameOnADevice() {
        DataSchema schema = DataSchema.builder("layout").field("value", FieldType.STRING).build();
        node.addVariable(new Variable("layout", schema, true, true, null));
        ObjectVariableService scoped = serviceWith(dashboardSchema(schema));
        when(mapper.auditDiff(any(), any())).thenReturn("{}");

        scoped.deleteVariable(PATH, "layout");

        assertThat(node.getVariable("layout")).isEmpty();
    }

    private ObjectVariableService serviceWith(BlueprintDefinition model) {
        BlueprintRegistry registry = new BlueprintRegistry();
        registry.register(model);
        return new ObjectVariableService(
                objectManager,
                variableRepository,
                mapper,
                telemetryPolicyService,
                javaFunctionRuntimeService,
                new FunctionScriptValidator(new ObjectMapper()),
                registry
        );
    }

    private static BlueprintDefinition dashboardSchema(DataSchema layout) {
        return new BlueprintDefinition(
                "dash-id",
                "dashboard-v1",
                "",
                BlueprintType.MIXIN,
                ObjectType.DASHBOARD,
                "",
                List.of(BlueprintVariableDefinition.of("layout", "", "info", layout, true, true, null)),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
        );
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
    void upsertFunctionRejectsInvalidScriptBeforeSaving() {
        FunctionDescriptor function = new FunctionDescriptor(
                "listRows",
                "List rows",
                null,
                null,
                "script",
                "{\"steps\":[{\"type\":\"selectAll\",\"var\":\"rows\",\"sql\":\"SELECT 1\"}]}",
                null,
                null
        );

        assertThatThrownBy(() -> variableService.upsertFunction(PATH, function))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listRows")
                .hasMessageContaining("Unknown script step type: selectAll");
        assertThat(node.functions()).doesNotContainKey("listRows");
        verify(javaFunctionRuntimeService, never()).syncOnSave(any(), any(), any());
        verify(objectManager, never()).persistNodeConfig(any(), any(), any(), any());
    }

    @Test
    void upsertFunctionSavesValidScript() {
        FunctionDescriptor function = new FunctionDescriptor(
                "ok",
                "Ok",
                null,
                null,
                "script",
                "{\"steps\":[{\"type\":\"return\",\"fields\":{\"ok\":true}}]}",
                null,
                null
        );
        when(mapper.auditDiff(any(), any())).thenReturn("{}");

        variableService.upsertFunction(PATH, function);

        assertThat(node.functions()).containsKey("ok");
        verify(objectManager).persistNodeConfig(eq(node), eq("UPSERT_FUNCTION"), eq("ok"), any());
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
