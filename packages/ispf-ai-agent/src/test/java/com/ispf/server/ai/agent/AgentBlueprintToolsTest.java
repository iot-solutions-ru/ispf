package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintApplyResult;
import com.ispf.plugin.blueprint.BlueprintAttachment;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
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
class AgentBlueprintToolsTest {

    @Mock
    private BlueprintRegistry BlueprintRegistry;
    @Mock
    private BlueprintApplicationService BlueprintApplicationService;
    @Mock
    private ObjectTreePort ObjectTreePort;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private TenantScopeService tenantScopeService;

    private List<PlatformAgentTool> tools;
    private AgentContext context;

    @BeforeEach
    void setUp() {
        tools = AgentBlueprintTools.all(
                BlueprintRegistry,
                BlueprintApplicationService,
                ObjectTreePort,
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
        BlueprintDefinition relative = sampleModel("virtual-lab-v1", BlueprintType.MIXIN);
        BlueprintDefinition instance = sampleModel("base-sensor-v1", BlueprintType.INSTANCE);
        when(BlueprintRegistry.all()).thenReturn(List.of(relative, instance));

        PlatformAgentTool tool = tools.stream()
                .filter(t -> "list_mixin_blueprints".equals(t.name()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(Map.of(), context);

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("count"));
    }

    @Test
    void listInstanceTypesFiltersByType() throws Exception {
        BlueprintDefinition relative = sampleModel("virtual-lab-v1", BlueprintType.MIXIN);
        BlueprintDefinition instance = sampleModel("base-sensor-v1", BlueprintType.INSTANCE);
        when(BlueprintRegistry.all()).thenReturn(List.of(relative, instance));

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
        BlueprintDefinition model = sampleModel("base-sensor-v1", BlueprintType.INSTANCE);
        PlatformObject instance = new PlatformObject("1", fullPath, ObjectType.DEVICE, name, "", "base-sensor-v1");

        when(BlueprintRegistry.findByName("base-sensor-v1")).thenReturn(Optional.of(model));
        when(tenantScopeService.isPathVisible(parent, context.authentication())).thenReturn(true);
        when(ObjectTreePort.tree()).thenReturn(new com.ispf.core.object.ObjectTree());
        when(ObjectTreePort.require(fullPath)).thenReturn(instance);
        when(BlueprintApplicationService.instantiateWithRules(model.id(), parent, name, Map.of()))
                .thenReturn(new BlueprintApplyResult(
                        new BlueprintAttachment("att-1", model.id(), model.name(), BlueprintType.INSTANCE, fullPath, Instant.now()),
                        List.of()
                ));

        PlatformAgentTool tool = tools.stream()
                .filter(t -> "instantiate_instance_type".equals(t.name()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(
                Map.of("parentPath", parent, "instanceName", name, "blueprintName", "base-sensor-v1"),
                context
        );

        assertEquals("OK", result.get("status"));
        assertEquals(fullPath, result.get("path"));
        verify(BlueprintApplicationService).instantiateWithRules(model.id(), parent, name, Map.of());
    }

    @Test
    void applyMixinBlueprintMergesStructure() throws Exception {
        String path = "root.platform.devices.pump-01";
        BlueprintDefinition model = sampleModel("virtual-lab-v1", BlueprintType.MIXIN);
        PlatformObject before = new PlatformObject("1", path, ObjectType.DEVICE, "Pump", "", null);
        PlatformObject after = new PlatformObject("1", path, ObjectType.DEVICE, "Pump", "", "virtual-lab-v1");
        after.addVariable(new com.ispf.core.object.Variable(
                "sineWave",
                com.ispf.core.model.DataSchema.builder("x").field("value", com.ispf.core.model.FieldType.DOUBLE).build(),
                true,
                false,
                null
        ));

        when(BlueprintRegistry.findByName("virtual-lab-v1")).thenReturn(Optional.of(model));
        when(tenantScopeService.isPathVisible(path, context.authentication())).thenReturn(true);
        when(ObjectTreePort.require(path)).thenReturn(before, after);
        when(BlueprintApplicationService.applyBlueprintWithRules(eq(model.id()), eq(path)))
                .thenReturn(new BlueprintApplyResult(
                        new BlueprintAttachment("att-1", model.id(), model.name(), BlueprintType.MIXIN, path, Instant.now()),
                        List.of()
                ));

        PlatformAgentTool tool = tools.stream()
                .filter(t -> "apply_mixin_blueprint".equals(t.name()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(Map.of("objectPath", path, "blueprintName", "virtual-lab-v1"), context);

        assertEquals("OK", result.get("status"));
        assertEquals(path, result.get("objectPath"));
        verify(objectAccessService).requireWrite(path, context.authentication());
        verify(BlueprintApplicationService).applyBlueprintWithRules(model.id(), path);
    }

    @Test
    void applyMixinBlueprintAcceptsModelAlias() throws Exception {
        String path = "root.platform.devices.pump-01";
        BlueprintDefinition model = sampleModel("virtual-lab-v1", BlueprintType.MIXIN);
        PlatformObject before = new PlatformObject("1", path, ObjectType.DEVICE, "Pump", "", null);
        PlatformObject after = new PlatformObject("1", path, ObjectType.DEVICE, "Pump", "", "virtual-lab-v1");

        when(BlueprintRegistry.findByName("virtual-lab-v1")).thenReturn(Optional.of(model));
        when(tenantScopeService.isPathVisible(path, context.authentication())).thenReturn(true);
        when(ObjectTreePort.require(path)).thenReturn(before, after);
        when(BlueprintApplicationService.applyBlueprintWithRules(eq(model.id()), eq(path)))
                .thenReturn(new BlueprintApplyResult(
                        new BlueprintAttachment("att-1", model.id(), model.name(), BlueprintType.MIXIN, path, Instant.now()),
                        List.of()
                ));

        PlatformAgentTool tool = tools.stream()
                .filter(t -> "apply_mixin_blueprint".equals(t.name()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = tool.execute(
                Map.of("objectPath", path, "model", "virtual-lab-v1"),
                context
        );

        assertEquals("OK", result.get("status"));
    }

    private static BlueprintDefinition sampleModel(String name, BlueprintType type) {
        return new BlueprintDefinition(
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
