package com.ispf.server.application.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.script.FunctionScriptEngine;
import com.ispf.server.binding.BindingRefreshAfterCommit;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationFunctionRuntimeEnsureDescriptorTest {

    private static final String PATH = "root.platform.singleton-blueprints.demo-hub-v1";
    private static final String FN = "demo_report";

    @Mock
    private ApplicationFunctionStore store;
    @Mock
    private FunctionScriptEngine scriptEngine;
    @Mock
    private ApplicationDataStore dataStore;
    @Mock
    private ApplicationSchemaSession schemaSession;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private BindingRefreshAfterCommit bindingRefreshAfterCommit;
    @Mock
    private TenantLocalDataAccessGuard tenantLocalDataAccessGuard;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ApplicationFunctionRuntime runtime;
    private PlatformObject node;

    @BeforeEach
    void setUp() {
        runtime = new ApplicationFunctionRuntime(
                store,
                scriptEngine,
                dataStore,
                schemaSession,
                objectManager,
                objectMapper,
                bindingRefreshAfterCommit,
                tenantLocalDataAccessGuard
        );
        node = new PlatformObject("n1", PATH, ObjectType.BLUEPRINT, "hub", "", null);
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(schemaSession).runWithPlatformCatalog(any());
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        }).when(schemaSession).runInSchema(anyString(), any());
        when(objectManager.require(PATH)).thenReturn(node);
        when(dataStore.findApp(anyString())).thenReturn(Optional.of(Map.of("schema_name", "app_demo")));
    }

    @Test
    void ensureDescriptorPreservesInvokeRolesWhenSchemasDiverge() throws Exception {
        DataSchema treeInput = DataSchema.builder("in")
                .field("from", FieldType.STRING)
                .field("extra", FieldType.STRING)
                .build();
        DataSchema storeInput = DataSchema.builder("in")
                .field("from", FieldType.STRING)
                .build();
        DataSchema output = DataSchema.builder("out")
                .field("error_code", FieldType.STRING)
                .build();

        node.addFunction(new FunctionDescriptor(
                FN,
                "Report",
                treeInput,
                output,
                "script",
                "{\"steps\":[]}",
                "root.platform.data-sources.demo",
                "1",
                List.of("admin", "auditor")
        ));

        ApplicationFunctionHandler.DeployedFunction deployed = new ApplicationFunctionHandler.DeployedFunction(
                UUID.randomUUID(),
                "demo",
                PATH,
                FN,
                "1",
                "script",
                "{\"steps\":[]}",
                objectMapper.writeValueAsString(storeInput),
                objectMapper.writeValueAsString(output)
        );
        when(store.findLatest(PATH, FN)).thenReturn(Optional.of(deployed));
        when(scriptEngine.execute(anyString(), any(), any(), any()))
                .thenReturn(DataRecord.single(output, Map.of("error_code", "OK")));

        runtime.invoke(PATH, FN, DataRecord.single(storeInput, Map.of("from", "2020-01-01")));

        FunctionDescriptor after = node.functions().get(FN);
        assertThat(after).isNotNull();
        assertThat(after.invokeRoles()).containsExactly("admin", "auditor");
        assertThat(after.sourceType()).isEqualTo("script");
        assertThat(after.sourceBody()).isEqualTo("{\"steps\":[]}");
        assertThat(after.dataSourcePath()).isEqualTo("root.platform.data-sources.demo");
        assertThat(after.inputSchema().fields()).hasSize(1);
        verify(objectManager).persistNodeTree(eq(PATH));
    }
}
