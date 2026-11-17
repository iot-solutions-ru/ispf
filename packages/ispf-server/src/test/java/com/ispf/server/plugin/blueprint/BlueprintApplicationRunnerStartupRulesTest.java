package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BlueprintApplicationRunnerStartupRulesTest {

    @Test
    void startupSyncDelegatesTemplatedRulesToPerRuleMergerAndAlignsHistory() {
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
                List.of(ruleWithRef("${parentPath}/sharedLimit"), BlueprintBindingRule.of("copy", "temperature", "temperature")),
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
        when(merger.syncBlueprintRulesAtStartup(eq(path), eq(model))).thenReturn(List.of());
        BindingRulesService bindingRules = mock(BindingRulesService.class);
        BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth();
        BlueprintApplicationRunner runner = runner(objectManager, registry, merger, bindingRules, health);

        runner.syncAllBlueprintBackedVariableMetadata();

        assertTrue(sensor.getVariable("temperature").orElseThrow().historyEnabled());
        verify(objectManager).persistNodeTree(path);
        verify(merger).syncBlueprintRulesAtStartup(eq(path), eq(model));
        assertTrue(health.isReady());
        verifyNoInteractions(bindingRules);
    }

    @Test
    void startupSyncMergesConcreteRulesViaStartupPathAndReportsReadinessProblems() {
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
        when(merger.syncBlueprintRulesAtStartup(eq(path), eq(model)))
                .thenReturn(List.of(path + "#limit: saved instance rule still contains ${...}"));
        BindingRulesService bindingRules = mock(BindingRulesService.class);
        BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth();

        runner(objectManager, registry, merger, bindingRules, health).syncAllBlueprintBackedVariableMetadata();

        verify(merger).syncBlueprintRulesAtStartup(eq(path), eq(model));
        assertFalse(health.isReady());
        verifyNoInteractions(bindingRules);
    }

    @Test
    void startupSyncSkipsDefinitionCatalogPaths() {
        String path = BlueprintCatalogRoots.INSTANCE + ".cold-chain";
        PlatformObject definition = new PlatformObject(
                "cold-chain",
                path,
                ObjectType.CUSTOM,
                "Cold chain type",
                "",
                "cold-chain"
        );
        BlueprintDefinition model = new BlueprintDefinition(
                "cold-chain",
                "cold-chain",
                "",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(),
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
        when(tree.all()).thenReturn(List.of(definition));
        BlueprintRegistry registry = mock(BlueprintRegistry.class);
        when(registry.findById("cold-chain")).thenReturn(Optional.of(model));
        BlueprintBindingRulesMerger merger = mock(BlueprintBindingRulesMerger.class);
        BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth();

        runner(objectManager, registry, merger, mock(BindingRulesService.class), health)
                .syncAllBlueprintBackedVariableMetadata();

        verifyNoInteractions(merger);
        assertTrue(health.isReady());
    }

    @Test
    void startupSyncSkipsSingletonCatalogStubWhenLivePathDiffers() {
        String catalogPath = BlueprintCatalogRoots.SINGLETON + ".doom";
        String livePath = "root.platform.apps.doom";
        PlatformObject stub = new PlatformObject(
                "doom",
                catalogPath,
                ObjectType.BLUEPRINT,
                "Doom stub",
                "",
                "doom"
        );
        PlatformObject live = new PlatformObject(
                "doom",
                livePath,
                ObjectType.CUSTOM,
                "Doom",
                "",
                "doom"
        );
        BlueprintDefinition model = new BlueprintDefinition(
                "doom",
                "doom",
                "",
                BlueprintType.SINGLETON,
                ObjectType.CUSTOM,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(BlueprintBindingRule.of("copy", "temperature", "temperature")),
                Map.of("singletonInstancePath", livePath),
                Instant.EPOCH,
                Instant.EPOCH
        );

        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.all()).thenReturn(List.of(stub, live));
        when(objectManager.require(livePath)).thenReturn(live);
        BlueprintRegistry registry = mock(BlueprintRegistry.class);
        when(registry.findById("doom")).thenReturn(Optional.of(model));
        BlueprintBindingRulesMerger merger = mock(BlueprintBindingRulesMerger.class);
        when(merger.syncBlueprintRulesAtStartup(eq(livePath), eq(model))).thenReturn(List.of());
        BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth();

        runner(objectManager, registry, merger, mock(BindingRulesService.class), health)
                .syncAllBlueprintBackedVariableMetadata();

        verify(merger).syncBlueprintRulesAtStartup(eq(livePath), eq(model));
        verifyNoMoreInteractions(merger);
        assertTrue(health.isReady());
    }

    @Test
    void startupSyncIncludesDefaultSingletonUnderCatalogPath() {
        String path = BlueprintCatalogRoots.SINGLETON + ".hub";
        PlatformObject singleton = new PlatformObject(
                "hub",
                path,
                ObjectType.CUSTOM,
                "Hub",
                "",
                "hub"
        );
        BlueprintDefinition model = new BlueprintDefinition(
                "hub",
                "hub",
                "",
                BlueprintType.SINGLETON,
                ObjectType.CUSTOM,
                "",
                List.of(),
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
        when(tree.all()).thenReturn(List.of(singleton));
        when(objectManager.require(path)).thenReturn(singleton);
        BlueprintRegistry registry = mock(BlueprintRegistry.class);
        when(registry.findById("hub")).thenReturn(Optional.of(model));
        BlueprintBindingRulesMerger merger = mock(BlueprintBindingRulesMerger.class);
        when(merger.syncBlueprintRulesAtStartup(eq(path), eq(model))).thenReturn(List.of());
        BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth();

        runner(objectManager, registry, merger, mock(BindingRulesService.class), health)
                .syncAllBlueprintBackedVariableMetadata();

        verify(merger).syncBlueprintRulesAtStartup(eq(path), eq(model));
        assertTrue(health.isReady());
    }

    private static BlueprintApplicationRunner runner(
            ObjectManager objectManager,
            BlueprintRegistry registry,
            BlueprintBindingRulesMerger merger,
            BindingRulesService bindingRules,
            BlueprintStartupRulesHealth health
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
                bindingRules,
                health
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
