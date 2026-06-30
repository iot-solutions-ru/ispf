package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelApplyResult;
import com.ispf.plugin.model.ModelAttachment;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.ModelType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.model.ModelApplicationService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentModelToolsTest {

    @Mock
    private ModelRegistry modelRegistry;
    @Mock
    private ModelApplicationService modelApplicationService;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private TenantScopeService tenantScopeService;

    private List<PlatformAgentTool> tools;
    private AgentContext context;

    @BeforeEach
    void setUp() {
        tools = AgentModelTools.all(
                modelRegistry,
                modelApplicationService,
                objectManager,
                objectAccessService,
                tenantScopeService
        );
        context = new AgentContext(
                "tester",
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                        .authenticated("tester", "n/a", List.of()),
                new AgentRunState(),
                AgentProfile.ADMIN,
                null
        );
    }

    @Test
    void listRelativeModelsFiltersByType() throws Exception {
        ModelDefinition relative = sampleModel("virtual-lab-v1", ModelType.RELATIVE);
        ModelDefinition instance = sampleModel("base-sensor-v1", ModelType.INSTANCE);
        when(modelRegistry.all()).thenReturn(List.of(relative, instance));

        PlatformAgentTool tool = tools.stream()
                .filter(t -> "list_relative_models".equals(t.name()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(Map.of(), context);

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("count"));
    }

    @Test
    void listInstanceTypesFiltersByType() throws Exception {
        ModelDefinition relative = sampleModel("virtual-lab-v1", ModelType.RELATIVE);
        ModelDefinition instance = sampleModel("base-sensor-v1", ModelType.INSTANCE);
        when(modelRegistry.all()).thenReturn(List.of(relative, instance));

        PlatformAgentTool tool = tools.stream()
                .filter(t -> "list_instance_types".equals(t.name()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(Map.of(), context);

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("count"));
    }

    @Test
    void instantiateInstanceTypeCreatesObject() throws Exception {
        String parent = "root.platform.devices";
        String name = "sensor-01";
        String fullPath = parent + "." + name;
        ModelDefinition model = sampleModel("base-sensor-v1", ModelType.INSTANCE);
        PlatformObject instance = new PlatformObject("1", fullPath, ObjectType.DEVICE, name, "", "base-sensor-v1");

        when(modelRegistry.findByName("base-sensor-v1")).thenReturn(Optional.of(model));
        when(tenantScopeService.isPathVisible(parent, context.authentication())).thenReturn(true);
        when(objectManager.tree()).thenReturn(new com.ispf.core.object.ObjectTree());
        when(objectManager.require(fullPath)).thenReturn(instance);
        when(modelApplicationService.instantiateWithRules(model.id(), parent, name, Map.of()))
                .thenReturn(new ModelApplyResult(
                        new ModelAttachment("att-1", model.id(), model.name(), ModelType.INSTANCE, fullPath, Instant.now()),
                        List.of()
                ));

        PlatformAgentTool tool = tools.stream()
                .filter(t -> "instantiate_instance_type".equals(t.name()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(
                Map.of("parentPath", parent, "instanceName", name, "modelName", "base-sensor-v1"),
                context
        );

        assertEquals("OK", result.get("status"));
        assertEquals(fullPath, result.get("path"));
        verify(modelApplicationService).instantiateWithRules(model.id(), parent, name, Map.of());
    }

    @Test
    void applyRelativeModelMergesStructure() throws Exception {
        String path = "root.platform.devices.pump-01";
        ModelDefinition model = sampleModel("virtual-lab-v1", ModelType.RELATIVE);
        PlatformObject before = new PlatformObject("1", path, ObjectType.DEVICE, "Pump", "", null);
        PlatformObject after = new PlatformObject("1", path, ObjectType.DEVICE, "Pump", "", "virtual-lab-v1");
        after.addVariable(new com.ispf.core.object.Variable(
                "sineWave",
                com.ispf.core.model.DataSchema.builder("x").field("value", com.ispf.core.model.FieldType.DOUBLE).build(),
                true,
                false,
                null
        ));

        when(modelRegistry.findByName("virtual-lab-v1")).thenReturn(Optional.of(model));
        when(tenantScopeService.isPathVisible(path, context.authentication())).thenReturn(true);
        when(objectManager.require(path)).thenReturn(before, after);
        when(modelApplicationService.applyModelWithRules(eq(model.id()), eq(path)))
                .thenReturn(new ModelApplyResult(
                        new ModelAttachment("att-1", model.id(), model.name(), ModelType.RELATIVE, path, Instant.now()),
                        List.of()
                ));

        PlatformAgentTool tool = tools.stream()
                .filter(t -> "apply_relative_model".equals(t.name()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(Map.of("objectPath", path, "modelName", "virtual-lab-v1"), context);

        assertEquals("OK", result.get("status"));
        assertEquals(path, result.get("objectPath"));
        verify(objectAccessService).requireWrite(path, context.authentication());
        verify(modelApplicationService).applyModelWithRules(model.id(), path);
    }

    @Test
    void applyRelativeModelAcceptsModelAlias() throws Exception {
        String path = "root.platform.devices.pump-01";
        ModelDefinition model = sampleModel("virtual-lab-v1", ModelType.RELATIVE);
        PlatformObject before = new PlatformObject("1", path, ObjectType.DEVICE, "Pump", "", null);
        PlatformObject after = new PlatformObject("1", path, ObjectType.DEVICE, "Pump", "", "virtual-lab-v1");

        when(modelRegistry.findByName("virtual-lab-v1")).thenReturn(Optional.of(model));
        when(tenantScopeService.isPathVisible(path, context.authentication())).thenReturn(true);
        when(objectManager.require(path)).thenReturn(before, after);
        when(modelApplicationService.applyModelWithRules(eq(model.id()), eq(path)))
                .thenReturn(new ModelApplyResult(
                        new ModelAttachment("att-1", model.id(), model.name(), ModelType.RELATIVE, path, Instant.now()),
                        List.of()
                ));

        PlatformAgentTool tool = tools.stream()
                .filter(t -> "apply_relative_model".equals(t.name()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(
                Map.of("objectPath", path, "model", "virtual-lab-v1"),
                context
        );

        assertEquals("OK", result.get("status"));
    }

    private static ModelDefinition sampleModel(String name, ModelType type) {
        return new ModelDefinition(
                "id-" + name,
                name,
                "test model",
                type,
                ObjectType.DEVICE,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );
    }
}
