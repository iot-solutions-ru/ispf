package com.ispf.server.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.datasource.DataSourceSqlSession;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlBindingObjectServiceDisableSubtreeTest {

    private static final String TARGET = "root.platform.devices.sensor";
    private static final String BINDING_PATH = SqlBindingObjectService.BINDINGS_ROOT + ".kpi";

    @Mock
    ObjectManager objectManager;
    @Mock
    SystemObjectStructureService structureService;
    @Mock
    DataSourceSqlSession dataSourceSqlSession;
    @Mock
    TenantLocalDataAccessGuard tenantLocalDataAccessGuard;
    @Mock
    ObjectTree objectTree;

    private SqlBindingObjectService service;

    @BeforeEach
    void setUp() {
        service = new SqlBindingObjectService(
                objectManager,
                structureService,
                dataSourceSqlSession,
                tenantLocalDataAccessGuard
        );
        when(objectManager.tree()).thenReturn(objectTree);
    }

    @Test
    void disableForTargetSubtreeSetsEnabledFalse() {
        when(objectTree.findByPath(SqlBindingObjectService.BINDINGS_ROOT))
                .thenReturn(Optional.of(folder(SqlBindingObjectService.BINDINGS_ROOT)));
        PlatformObject bindingNode = bindingNode(BINDING_PATH, TARGET, true);
        when(objectTree.childrenOf(SqlBindingObjectService.BINDINGS_ROOT)).thenReturn(List.of(bindingNode));

        int disabled = service.disableForTargetSubtree(TARGET);

        assertThat(disabled).isEqualTo(1);
        verify(objectManager).setVariableValue(eq(BINDING_PATH), eq("enabled"), any(DataRecord.class));
    }

    @Test
    void disableForTargetSubtreeSkipsAlreadyDisabledAndUnrelated() {
        when(objectTree.findByPath(SqlBindingObjectService.BINDINGS_ROOT))
                .thenReturn(Optional.of(folder(SqlBindingObjectService.BINDINGS_ROOT)));
        PlatformObject disabled = bindingNode(BINDING_PATH + "1", TARGET, false);
        PlatformObject other = bindingNode(BINDING_PATH + "2", "root.other", true);
        when(objectTree.childrenOf(SqlBindingObjectService.BINDINGS_ROOT)).thenReturn(List.of(disabled, other));

        assertThat(service.disableForTargetSubtree(TARGET)).isZero();
        verify(objectManager, never()).setVariableValue(any(), eq("enabled"), any());
    }

    @Test
    void disableForTargetSubtreeMatchesDescendants() {
        when(objectTree.findByPath(SqlBindingObjectService.BINDINGS_ROOT))
                .thenReturn(Optional.of(folder(SqlBindingObjectService.BINDINGS_ROOT)));
        PlatformObject childTarget = bindingNode(BINDING_PATH, TARGET + ".child", true);
        when(objectTree.childrenOf(SqlBindingObjectService.BINDINGS_ROOT)).thenReturn(List.of(childTarget));

        assertThat(service.disableForTargetSubtree(TARGET)).isEqualTo(1);
        verify(objectManager).setVariableValue(eq(BINDING_PATH), eq("enabled"), any(DataRecord.class));
    }

    private static PlatformObject folder(String path) {
        return new PlatformObject("id-" + path, path, ObjectType.BINDINGS, path, "", null);
    }

    private static PlatformObject bindingNode(String path, String targetObjectPath, boolean enabled) {
        PlatformObject node = new PlatformObject("id-" + path, path, ObjectType.BINDING, path, "", null);
        node.addVariable(stringVar("targetObjectPath", targetObjectPath));
        node.addVariable(stringVar("variable", "value"));
        node.addVariable(stringVar("dataSourcePath", "root.ds"));
        node.addVariable(stringVar("query", "SELECT 1 AS value"));
        node.addVariable(stringVar("valueField", "value"));
        node.addVariable(stringVar("refresh", "on_schedule"));
        node.addVariable(longVar("refreshIntervalMs", 30_000L));
        node.addVariable(stringVar("triggerObjectPath", ""));
        node.addVariable(stringVar("triggerFunctionName", ""));
        node.addVariable(boolVar("enabled", enabled));
        return node;
    }

    private static Variable stringVar(String name, String value) {
        DataSchema schema = DataSchema.builder(name).field("value", FieldType.STRING).build();
        return new Variable(name, schema, true, true, DataRecord.single(schema, Map.of("value", value)));
    }

    private static Variable boolVar(String name, boolean value) {
        DataSchema schema = DataSchema.builder(name).field("value", FieldType.BOOLEAN).build();
        return new Variable(name, schema, true, true, DataRecord.single(schema, Map.of("value", value)));
    }

    private static Variable longVar(String name, long value) {
        DataSchema schema = DataSchema.builder(name).field("value", FieldType.INTEGER).build();
        return new Variable(name, schema, true, true, DataRecord.single(schema, Map.of("value", (int) value)));
    }
}
