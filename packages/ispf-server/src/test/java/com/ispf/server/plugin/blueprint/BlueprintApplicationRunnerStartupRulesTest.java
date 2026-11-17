package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.BlueprintContribution;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        when(merger.syncBlueprintRulesAtStartup(eq(path), eq(model), eq(Set.of()))).thenReturn(List.of());
        BindingRulesService bindingRules = mock(BindingRulesService.class);
        BlueprintStartupRulesHealth health = health(objectManager);
        BlueprintApplicationRunner runner = runner(objectManager, registry, merger, bindingRules, health);

        runner.syncAllBlueprintBackedVariableMetadata();

        assertTrue(sensor.getVariable("temperature").orElseThrow().historyEnabled());
        verify(objectManager).persistNodeTree(path);
        verify(merger).syncBlueprintRulesAtStartup(eq(path), eq(model), eq(Set.of()));
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
        when(tree.findByPath(path)).thenReturn(Optional.of(sensor));
        when(objectManager.require(path)).thenReturn(sensor);
        BlueprintRegistry registry = mock(BlueprintRegistry.class);
        when(registry.findById("demo-sensor")).thenReturn(Optional.of(model));
        BlueprintBindingRulesMerger merger = mock(BlueprintBindingRulesMerger.class);
        when(merger.syncBlueprintRulesAtStartup(eq(path), eq(model), eq(Set.of())))
                .thenReturn(List.of(BlueprintStartupRulesHealth.Problem.of(
                        path, model, "limit", BlueprintStartupRulesHealth.Kind.UNRESOLVED
                )));
        BindingRulesService bindingRules = mock(BindingRulesService.class);
        BlueprintStartupRulesHealth health = health(objectManager);

        runner(objectManager, registry, merger, bindingRules, health).syncAllBlueprintBackedVariableMetadata();

        verify(merger).syncBlueprintRulesAtStartup(eq(path), eq(model), eq(Set.of()));
        assertFalse(health.isReady());
        verifyNoInteractions(bindingRules);
    }

    @Test
    void startupSyncCoversMixinFromAppliedBlueprintIdsWithoutTemplateId() {
        String path = "root.platform.devices.pump";
        DataSchema schema = DataSchema.builder("reading").field("value", FieldType.DOUBLE).build();
        PlatformObject pump = new PlatformObject("pump", path, ObjectType.CUSTOM, "Pump", "", null);
        pump.addVariable(new Variable("alarmLimit", schema, true, true, null));
        pump.setappliedBlueprintIds(List.of("alarm-mixin"));
        BlueprintDefinition mixin = model(
                "alarm-mixin",
                BlueprintType.MIXIN,
                BlueprintVariableDefinition.withHistory("alarmLimit", "", "default", schema, true, true, null),
                BlueprintBindingRule.of("alarm", "alarmLimit", "alarmLimit > 0")
        );

        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.all()).thenReturn(List.of(pump));
        when(objectManager.require(path)).thenReturn(pump);
        BlueprintRegistry registry = mock(BlueprintRegistry.class);
        when(registry.findById("alarm-mixin")).thenReturn(Optional.of(mixin));
        BlueprintBindingRulesMerger merger = mock(BlueprintBindingRulesMerger.class);
        BlueprintStartupRulesHealth health = health(objectManager);

        runner(objectManager, registry, merger, mock(BindingRulesService.class), health)
                .syncAllBlueprintBackedVariableMetadata();

        assertTrue(pump.getVariable("alarmLimit").orElseThrow().historyEnabled());
        verify(merger).syncBlueprintRulesAtStartup(eq(path), eq(mixin), eq(Set.of()));
        verifyNoMoreInteractions(merger);
        verify(objectManager).persistNodeTree(path);
        assertTrue(health.isReady());
    }

    @Test
    void startupSyncCoversPrimaryAndMixinOnceEachAndLeavesSharedNamesToTheirOwner() {
        String path = "root.platform.devices.hub.sensor";
        DataSchema schema = DataSchema.builder("reading").field("value", FieldType.DOUBLE).build();
        PlatformObject sensor = new PlatformObject("sensor", path, ObjectType.CUSTOM, "Sensor", "", "cold-chain");
        sensor.addVariable(new Variable("temperature", schema, true, true, null));
        sensor.setappliedBlueprintIds(List.of("cold-chain", "plain-mixin"));
        sensor.putBlueprintContribution("plain-mixin", new BlueprintContribution(
                List.of("temperature"), List.of(), List.of(), List.of("copy")
        ));
        // Primary re-applied after the mixin: last apply owns the shared variable and rule.
        sensor.putBlueprintContribution("cold-chain", new BlueprintContribution(
                List.of("temperature"), List.of(), List.of(), List.of("limit", "copy")
        ));
        BlueprintDefinition primary = new BlueprintDefinition(
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
                List.of(
                        BlueprintBindingRule.of("limit", "temperature", "1"),
                        BlueprintBindingRule.of("copy", "temperature", "temperature")
                ),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
        );
        BlueprintDefinition mixin = model(
                "plain-mixin",
                BlueprintType.MIXIN,
                BlueprintVariableDefinition.of("temperature", "", "default", schema, true, true, null),
                BlueprintBindingRule.of("copy", "temperature", "temperature * 2")
        );

        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.all()).thenReturn(List.of(sensor));
        when(objectManager.require(path)).thenReturn(sensor);
        BlueprintRegistry registry = mock(BlueprintRegistry.class);
        when(registry.findById("cold-chain")).thenReturn(Optional.of(primary));
        when(registry.findById("plain-mixin")).thenReturn(Optional.of(mixin));
        BlueprintBindingRulesMerger merger = mock(BlueprintBindingRulesMerger.class);

        runner(objectManager, registry, merger, mock(BindingRulesService.class), health(objectManager))
                .syncAllBlueprintBackedVariableMetadata();

        assertTrue(sensor.getVariable("temperature").orElseThrow().historyEnabled());
        verify(merger, times(1)).syncBlueprintRulesAtStartup(eq(path), eq(primary), eq(Set.of()));
        verify(merger, times(1)).syncBlueprintRulesAtStartup(eq(path), eq(mixin), eq(Set.of("copy")));
        verifyNoMoreInteractions(merger);
        verify(objectManager, times(1)).persistNodeTree(path);
    }

    @Test
    void startupSyncFailureOnOneBlueprintDoesNotSkipTheOthers() {
        String path = "root.platform.devices.pump";
        DataSchema schema = DataSchema.builder("reading").field("value", FieldType.DOUBLE).build();
        PlatformObject pump = new PlatformObject("pump", path, ObjectType.CUSTOM, "Pump", "", "pump-type");
        pump.addVariable(new Variable("flow", schema, true, true, null));
        pump.setappliedBlueprintIds(List.of("pump-type", "broken-mixin"));
        BlueprintDefinition primary = model(
                "pump-type",
                BlueprintType.INSTANCE,
                BlueprintVariableDefinition.withHistory("flow", "", "default", schema, true, true, null),
                BlueprintBindingRule.of("copy", "flow", "flow")
        );
        BlueprintDefinition broken = model(
                "broken-mixin",
                BlueprintType.MIXIN,
                BlueprintVariableDefinition.of("pressure", "", "default", schema, true, true, null),
                BlueprintBindingRule.of("bad", "missingVariable", "flow")
        );

        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.all()).thenReturn(List.of(pump));
        when(objectManager.require(path)).thenReturn(pump);
        BlueprintRegistry registry = mock(BlueprintRegistry.class);
        when(registry.findById("pump-type")).thenReturn(Optional.of(primary));
        when(registry.findById("broken-mixin")).thenReturn(Optional.of(broken));
        BlueprintBindingRulesMerger merger = mock(BlueprintBindingRulesMerger.class);
        when(merger.syncBlueprintRulesAtStartup(eq(path), eq(broken), any()))
                .thenThrow(new IllegalArgumentException("Unknown target variable"));
        BlueprintStartupRulesHealth health = health(objectManager);

        runner(objectManager, registry, merger, mock(BindingRulesService.class), health)
                .syncAllBlueprintBackedVariableMetadata();

        assertTrue(pump.getVariable("flow").orElseThrow().historyEnabled());
        verify(merger).syncBlueprintRulesAtStartup(eq(path), eq(primary), eq(Set.of()));
        verify(objectManager).persistNodeTree(path);
        assertTrue(health.isReady());
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
        BlueprintStartupRulesHealth health = health(objectManager);

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
        when(merger.syncBlueprintRulesAtStartup(eq(livePath), eq(model), eq(Set.of()))).thenReturn(List.of());
        BlueprintStartupRulesHealth health = health(objectManager);

        runner(objectManager, registry, merger, mock(BindingRulesService.class), health)
                .syncAllBlueprintBackedVariableMetadata();

        verify(merger).syncBlueprintRulesAtStartup(eq(livePath), eq(model), eq(Set.of()));
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
        when(merger.syncBlueprintRulesAtStartup(eq(path), eq(model), eq(Set.of()))).thenReturn(List.of());
        BlueprintStartupRulesHealth health = health(objectManager);

        runner(objectManager, registry, merger, mock(BindingRulesService.class), health)
                .syncAllBlueprintBackedVariableMetadata();

        verify(merger).syncBlueprintRulesAtStartup(eq(path), eq(model), eq(Set.of()));
        assertTrue(health.isReady());
    }

    private static BlueprintStartupRulesHealth health(ObjectManager objectManager) {
        return new BlueprintStartupRulesHealth(objectManager, mock(BindingRulesService.class));
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

    private static BlueprintDefinition model(
            String id,
            BlueprintType type,
            BlueprintVariableDefinition variable,
            BlueprintBindingRule rule
    ) {
        return new BlueprintDefinition(
                id,
                id,
                "",
                type,
                ObjectType.CUSTOM,
                "",
                List.of(variable),
                List.of(),
                List.of(),
                List.of(rule),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
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
