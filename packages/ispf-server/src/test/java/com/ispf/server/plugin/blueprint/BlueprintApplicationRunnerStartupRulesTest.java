package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.server.dashboard.DashboardDemoRulesBootstrap;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectBindingStatePort;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BlueprintApplicationRunnerStartupRulesTest {

    @Test
    void startupSyncKeepsDeployedRulesAndStillAlignsHistory() {
        String path = "root.platform.devices.hub.sensor";
        DataSchema schema = DataSchema.builder("reading").field("value", FieldType.DOUBLE).build();
        PlatformObject sensor = new PlatformObject(
                "sensor",
                path,
                ObjectType.DEVICE,
                "Sensor",
                "",
                "cold-chain"
        );
        sensor.addVariable(new Variable("temperature", schema, true, true, null));

        BlueprintDefinition model = new BlueprintDefinition(
                "cold-chain",
                "cold-chain",
                "",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(BlueprintVariableDefinition.withHistory(
                        "temperature", "", "default", schema, true, true, null
                )),
                List.of(),
                List.of(),
                List.of(ruleWithRef("${parentPath}/sharedLimit")),
                Map.of("code", "DEFAULT", "parentPath", "root.platform.devices.hub"),
                Instant.EPOCH,
                Instant.EPOCH
        );

        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.all()).thenReturn(List.of(sensor));
        when(objectManager.require(path)).thenReturn(sensor);

        BlueprintRegistry registry = mock(BlueprintRegistry.class);
        when(registry.findById("cold-chain")).thenReturn(Optional.of(model));

        BlueprintBindingRulesMerger merger = mock(BlueprintBindingRulesMerger.class);
        BindingRulesService bindingRules = mock(BindingRulesService.class);
        BlueprintApplicationRunner runner = new BlueprintApplicationRunner(
                mock(com.ispf.plugin.blueprint.BlueprintEngine.class),
                registry,
                objectManager,
                merger,
                mock(BlueprintApplicationService.class),
                mock(SystemObjectStructureService.class),
                mock(DeviceProvisioningService.class),
                mock(DashboardDemoRulesBootstrap.class),
                mock(ObjectBindingStatePort.class),
                bindingRules
        );

        runner.syncAllBlueprintBackedVariableMetadata();

        assertTrue(sensor.getVariable("temperature").orElseThrow().historyEnabled());
        verify(objectManager).persistNodeTree(path);
        verifyNoInteractions(merger, bindingRules);
    }

    @Test
    void startupSyncMergesConcreteRulesWithoutEvaluatingThem() {
        String path = "root.platform.devices.demo-sensor-01";
        DataSchema schema = DataSchema.builder("reading").field("value", FieldType.DOUBLE).build();
        PlatformObject sensor = new PlatformObject(
                "demo",
                path,
                ObjectType.DEVICE,
                "Demo",
                "",
                "demo-sensor"
        );
        sensor.addVariable(new Variable("temperature", schema, true, true, null));
        BlueprintDefinition model = new BlueprintDefinition(
                "demo-sensor",
                "demo-sensor",
                "",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(BlueprintVariableDefinition.withHistory(
                        "temperature", "", "default", schema, true, true, null
                )),
                List.of(),
                List.of(),
                List.of(BlueprintBindingRule.of("copy", "temperature", "temperature")),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
        );

        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.all()).thenReturn(List.of(sensor));
        when(objectManager.require(path)).thenReturn(sensor);
        BlueprintRegistry registry = mock(BlueprintRegistry.class);
        when(registry.findById("demo-sensor")).thenReturn(Optional.of(model));
        BlueprintBindingRulesMerger merger = mock(BlueprintBindingRulesMerger.class);
        BindingRulesService bindingRules = mock(BindingRulesService.class);

        runner(objectManager, registry, merger, bindingRules).syncAllBlueprintBackedVariableMetadata();

        verify(merger).mergeBlueprintRules(eq(path), eq(model), eq(model.parameters()), eq(false));
        verifyNoInteractions(bindingRules);
    }

    private static BlueprintApplicationRunner runner(
            ObjectManager objectManager,
            BlueprintRegistry registry,
            BlueprintBindingRulesMerger merger,
            BindingRulesService bindingRules
    ) {
        return new BlueprintApplicationRunner(
                mock(com.ispf.plugin.blueprint.BlueprintEngine.class),
                registry,
                objectManager,
                merger,
                mock(BlueprintApplicationService.class),
                mock(SystemObjectStructureService.class),
                mock(DeviceProvisioningService.class),
                mock(DashboardDemoRulesBootstrap.class),
                mock(ObjectBindingStatePort.class),
                bindingRules
        );
    }

    private static BlueprintBindingRule ruleWithRef(String ref) {
        return new BlueprintBindingRule(
                "limit",
                "limit",
                true,
                0,
                null,
                "",
                "1",
                "sharedLimit",
                "value",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BindingTarget("variable", "sharedLimit", "value", null, null, ref)
        );
    }
}
