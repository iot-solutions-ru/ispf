package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.platform.analytics.formula.BindingFormulaResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BlueprintBindingRulesMergerStartupSyncTest {

    @Test
    void startupSyncKeepsSavedTemplatedRuleMergesCleanSiblingWithoutEvaluating() {
        BlueprintRegistry registry = new BlueprintRegistry();
        BlueprintBindingRule templated = new BlueprintBindingRule(
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
                new BindingTarget("variable", "sharedLimit", "value", null, null, "${parentPath}/sharedLimit")
        );
        BlueprintBindingRule concrete = BlueprintBindingRule.of("copy", "temperature", "temperature");
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
                List.of(templated, concrete),
                Map.of("parentPath", "root.platform.devices.hub"),
                Instant.EPOCH,
                Instant.EPOCH
        );
        registry.register(model);

        BindingRule savedLimit = new BindingRule(
                "limit",
                "limit",
                true,
                0,
                null,
                "",
                "1",
                new BindingTarget(
                        "variable",
                        "sharedLimit",
                        "value",
                        null,
                        null,
                        "root.platform.devices.hub/sharedLimit"
                )
        );
        BindingRulesService rulesService = mock(BindingRulesService.class);
        when(rulesService.listRules("root.platform.devices.hub.sensor")).thenReturn(List.of(savedLimit));
        BindingFormulaResolver formulaResolver = mock(BindingFormulaResolver.class);
        when(formulaResolver.resolve(any())).thenAnswer(inv -> inv.getArgument(0));
        BindingRuleEngine ruleEngine = mock(BindingRuleEngine.class);
        BindingDependencyIndex depIndex = mock(BindingDependencyIndex.class);

        BlueprintBindingRulesMerger merger = new BlueprintBindingRulesMerger(
                rulesService,
                depIndex,
                ruleEngine,
                formulaResolver,
                mock(BlueprintAnalyticsFormulaSupport.class),
                new BlueprintParameterResolver(),
                registry
        );

        List<BlueprintStartupRulesHealth.Problem> problems = merger.syncBlueprintRulesAtStartup(
                "root.platform.devices.hub.sensor",
                model,
                Set.of()
        );

        assertThat(problems).isEmpty();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BindingRule>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(rulesService).saveRulesAllowingUnresolvedPlaceholders(
                eq("root.platform.devices.hub.sensor"),
                savedCaptor.capture()
        );
        List<BindingRule> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        BindingRule kept = saved.stream().filter(r -> r.id().equals("limit")).findFirst().orElseThrow();
        assertThat(kept.target().ref()).isEqualTo("root.platform.devices.hub/sharedLimit");
        BindingRule mergedCopy = saved.stream().filter(r -> r.id().equals("copy")).findFirst().orElseThrow();
        assertThat(mergedCopy.expression()).isEqualTo("temperature");
        verify(depIndex).rebuild("root.platform.devices.hub.sensor");
        verifyNoInteractions(ruleEngine);
    }

    @Test
    void startupSyncReportsMissingAndUnresolvedSavedTemplatedRules() {
        BlueprintRegistry registry = new BlueprintRegistry();
        BlueprintBindingRule missing = new BlueprintBindingRule(
                "missing",
                "missing",
                true,
                0,
                null,
                "",
                "1",
                "out",
                "value",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BindingTarget("variable", "out", "value", null, null, "${parentPath}/out")
        );
        BlueprintBindingRule unresolved = new BlueprintBindingRule(
                "bad",
                "bad",
                true,
                0,
                null,
                "",
                "read(\"${parentPath}/x\")",
                "out",
                "value",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BindingTarget("variable", "out", "value", null, null, null)
        );
        BlueprintDefinition model = new BlueprintDefinition(
                "templated",
                "templated",
                "",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(missing, unresolved),
                Map.of("parentPath", "root.hub"),
                Instant.EPOCH,
                Instant.EPOCH
        );
        registry.register(model);

        BindingRule stillRaw = new BindingRule(
                "bad",
                "bad",
                true,
                0,
                null,
                "",
                "read(\"${parentPath}/x\")",
                new BindingTarget("variable", "out", "value", null, null, null)
        );
        BindingRulesService rulesService = mock(BindingRulesService.class);
        when(rulesService.listRules("root.platform.devices.obj")).thenReturn(List.of(stillRaw));
        BindingFormulaResolver formulaResolver = mock(BindingFormulaResolver.class);
        when(formulaResolver.resolve(any())).thenAnswer(inv -> inv.getArgument(0));

        BlueprintBindingRulesMerger merger = new BlueprintBindingRulesMerger(
                rulesService,
                mock(BindingDependencyIndex.class),
                mock(BindingRuleEngine.class),
                formulaResolver,
                mock(BlueprintAnalyticsFormulaSupport.class),
                new BlueprintParameterResolver(),
                registry
        );

        List<BlueprintStartupRulesHealth.Problem> problems =
                merger.syncBlueprintRulesAtStartup("root.platform.devices.obj", model, Set.of());

        assertThat(problems)
                .extracting(BlueprintStartupRulesHealth.Problem::ruleId, BlueprintStartupRulesHealth.Problem::kind)
                .containsExactly(
                        tuple("missing", BlueprintStartupRulesHealth.Kind.MISSING),
                        tuple("bad", BlueprintStartupRulesHealth.Kind.UNRESOLVED)
                );
        assertThat(problems.get(0).message()).contains("missing").contains("no saved");
        assertThat(problems.get(1).message()).contains("bad").contains("${...}");
        assertThat(problems).allSatisfy(problem -> assertThat(problem.blueprintId()).isEqualTo("templated"));
        verify(rulesService).saveRulesAllowingUnresolvedPlaceholders(eq("root.platform.devices.obj"), any());
    }

    @Test
    void startupSyncDoesNotApplyBlueprintParameterDefaults() {
        BlueprintRegistry registry = new BlueprintRegistry();
        BlueprintBindingRule concrete = new BlueprintBindingRule(
                "copy",
                "Copy ${code}",
                true,
                0,
                null,
                "",
                "temperature",
                "temperature",
                "value",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BindingTarget("variable", "temperature", "value", null, null, null)
        );
        // name has ${code} → treated as templated, keep saved
        BlueprintDefinition model = new BlueprintDefinition(
                "demo",
                "demo",
                "",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(concrete),
                Map.of("code", "DEFAULT"),
                Instant.EPOCH,
                Instant.EPOCH
        );
        registry.register(model);

        BindingRule saved = new BindingRule(
                "copy",
                "Copy chamber-1",
                true,
                0,
                null,
                "",
                "temperature",
                new BindingTarget("variable", "temperature", "value", null, null, null)
        );
        BindingRulesService rulesService = mock(BindingRulesService.class);
        when(rulesService.listRules("root.obj")).thenReturn(List.of(saved));
        BindingFormulaResolver formulaResolver = mock(BindingFormulaResolver.class);
        when(formulaResolver.resolve(any())).thenAnswer(inv -> inv.getArgument(0));

        BlueprintBindingRulesMerger merger = new BlueprintBindingRulesMerger(
                rulesService,
                mock(BindingDependencyIndex.class),
                mock(BindingRuleEngine.class),
                formulaResolver,
                mock(BlueprintAnalyticsFormulaSupport.class),
                new BlueprintParameterResolver(),
                registry
        );

        assertThat(merger.syncBlueprintRulesAtStartup("root.obj", model, Set.of())).isEmpty();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BindingRule>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(rulesService).saveRulesAllowingUnresolvedPlaceholders(eq("root.obj"), savedCaptor.capture());
        assertThat(savedCaptor.getValue().getFirst().name()).isEqualTo("Copy chamber-1");
    }

    @Test
    void startupSyncLeavesRuleOwnedByAnotherBlueprintAsSaved() {
        BlueprintRegistry registry = new BlueprintRegistry();
        BlueprintDefinition model = new BlueprintDefinition(
                "primary",
                "primary",
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
        registry.register(model);

        BindingRule mixinCopy = new BindingRule(
                "copy",
                "copy",
                true,
                0,
                null,
                "",
                "temperature * 2",
                new BindingTarget("variable", "temperature", "value", null, null, null)
        );
        BindingRulesService rulesService = mock(BindingRulesService.class);
        when(rulesService.listRules("root.obj")).thenReturn(List.of(mixinCopy));
        BindingFormulaResolver formulaResolver = mock(BindingFormulaResolver.class);
        when(formulaResolver.resolve(any())).thenAnswer(inv -> inv.getArgument(0));

        BlueprintBindingRulesMerger merger = new BlueprintBindingRulesMerger(
                rulesService,
                mock(BindingDependencyIndex.class),
                mock(BindingRuleEngine.class),
                formulaResolver,
                mock(BlueprintAnalyticsFormulaSupport.class),
                new BlueprintParameterResolver(),
                registry
        );

        assertThat(merger.syncBlueprintRulesAtStartup("root.obj", model, Set.of("copy"))).isEmpty();
        verify(rulesService).saveRulesAllowingUnresolvedPlaceholders("root.obj", List.of(mixinCopy));
    }

    @Test
    void removeBlueprintRulesKeepsUnresolvedSiblingWithoutStrictValidation() {
        BindingRule unresolvedSibling = new BindingRule(
                "limit",
                "limit",
                true,
                0,
                null,
                "",
                "1",
                new BindingTarget("variable", "sharedLimit", "value", null, null, "${parentPath}/sharedLimit")
        );
        BindingRule owned = new BindingRule(
                "copy",
                "copy",
                true,
                0,
                null,
                "",
                "temperature",
                new BindingTarget("variable", "temperature", "value", null, null, null)
        );
        BindingRulesService rulesService = mock(BindingRulesService.class);
        when(rulesService.listRules("root.obj")).thenReturn(List.of(unresolvedSibling, owned));
        BindingDependencyIndex depIndex = mock(BindingDependencyIndex.class);

        BlueprintBindingRulesMerger merger = new BlueprintBindingRulesMerger(
                rulesService,
                depIndex,
                mock(BindingRuleEngine.class),
                mock(BindingFormulaResolver.class),
                mock(BlueprintAnalyticsFormulaSupport.class),
                new BlueprintParameterResolver(),
                new BlueprintRegistry()
        );

        merger.removeBlueprintRules("root.obj", List.of("copy"));

        verify(rulesService).saveRulesAllowingUnresolvedPlaceholders("root.obj", List.of(unresolvedSibling));
        verify(rulesService, never()).saveRules(any(), any());
        verify(depIndex).rebuild("root.obj");
    }
}
