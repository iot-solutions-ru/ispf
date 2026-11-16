package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.expression.ExpressionEngine;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintSqlBindingTemplate;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.BundleManifestValidator;
import com.ispf.server.application.bundle.BundleValidationResult;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.formula.BindingFormulaResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlueprintContractAndRuleResolutionTest {

    @Test
    void mergerResolvesTargetRefAndInheritedParentRules() {
        BlueprintRegistry registry = new BlueprintRegistry();
        BlueprintDefinition base = new BlueprintDefinition(
                "base-id",
                "base-cold-chain",
                "",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(new BlueprintBindingRule(
                        "limit-${code}",
                        "Limit ${code}",
                        true,
                        1,
                        null,
                        "",
                        "read(\"${parentPath}/sharedLimit\")",
                        null,
                        "value",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BindingTarget("variable", null, "value", null, null, "${parentPath}/out_${code}")
                )),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
        );
        BlueprintDefinition child = new BlueprintDefinition(
                "child-id",
                "child-cold-chain",
                "",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of("extendsBlueprintId", base.id()),
                Instant.EPOCH,
                Instant.EPOCH
        );
        registry.register(base);
        registry.register(child);

        BindingRulesService rulesService = mock(BindingRulesService.class);
        when(rulesService.listRules("root.platform.instances.hub.chamber-1")).thenReturn(List.of());
        BindingDependencyIndex depIndex = mock(BindingDependencyIndex.class);
        BindingRuleEngine ruleEngine = mock(BindingRuleEngine.class);
        BindingFormulaResolver formulaResolver = mock(BindingFormulaResolver.class);
        when(formulaResolver.resolve(any())).thenAnswer(inv -> inv.getArgument(0));
        BlueprintAnalyticsFormulaSupport formulaSupport = mock(BlueprintAnalyticsFormulaSupport.class);

        BlueprintBindingRulesMerger merger = new BlueprintBindingRulesMerger(
                rulesService,
                depIndex,
                ruleEngine,
                formulaResolver,
                formulaSupport,
                new BlueprintParameterResolver(),
                registry
        );

        merger.mergeBlueprintRules(
                "root.platform.instances.hub.chamber-1",
                child,
                Map.of("parentPath", "root.platform.instances.hub", "code", "c1"),
                false
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BindingRule>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(rulesService).saveRules(eq("root.platform.instances.hub.chamber-1"), savedCaptor.capture());
        assertThat(savedCaptor.getValue()).hasSize(1);
        BindingRule saved = savedCaptor.getValue().getFirst();
        assertThat(saved.id()).isEqualTo("limit-${code}");
        assertThat(saved.name()).isEqualTo("Limit c1");
        assertThat(saved.expression()).isEqualTo("read(\"root.platform.instances.hub/sharedLimit\")");
        assertThat(saved.target().ref()).isEqualTo("root.platform.instances.hub/out_c1");
    }

    @Test
    void mergerResolvesTemplatedActivatorRefsWithoutEagerParseFailure() {
        BlueprintRegistry registry = new BlueprintRegistry();
        BlueprintDefinition model = new BlueprintDefinition(
                "templated-activators",
                "templated-activators",
                "",
                BlueprintType.INSTANCE,
                ObjectType.CUSTOM,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(new BlueprintBindingRule(
                        "mirror",
                        "Mirror ${code}",
                        true,
                        1,
                        new BindingActivators(
                                false,
                                List.of(new BindingVariableRef(
                                        null,
                                        null,
                                        "${parentPath}/sharedLimit"
                                )),
                                null,
                                0L,
                                false,
                                false,
                                "${parentPath}/evt/alarm"
                        ),
                        "",
                        "read(\"${parentPath}/sharedLimit\")",
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
                )),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
        );
        registry.register(model);

        BindingRulesService rulesService = mock(BindingRulesService.class);
        when(rulesService.listRules("root.platform.instances.hub.chamber-1")).thenReturn(List.of());
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

        merger.mergeBlueprintRules(
                "root.platform.instances.hub.chamber-1",
                model,
                Map.of("parentPath", "root.platform.instances.hub", "code", "c1"),
                false
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BindingRule>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(rulesService).saveRules(eq("root.platform.instances.hub.chamber-1"), savedCaptor.capture());
        BindingRule saved = savedCaptor.getValue().getFirst();
        assertThat(saved.activators().onVariableChange()).hasSize(1);
        assertThat(saved.activators().onVariableChange().getFirst().ref())
                .isEqualTo("root.platform.instances.hub/sharedLimit");
        assertThat(saved.activators().onVariableChange().getFirst().objectPath())
                .isEqualTo("root.platform.instances.hub");
        assertThat(saved.activators().onEventRef()).isEqualTo("root.platform.instances.hub/evt/alarm");
    }

    @Test
    void typedMixinFacadeBlocksManualApplyAndDetachWhenSuitabilityExpressionPresent() {
        ObjectTree tree = new ObjectTree();
        tree.register(new PlatformObject("p", "root.platform", ObjectType.PLATFORM, "Platform", "", null));
        BlueprintRegistry registry = new BlueprintRegistry();
        BlueprintEngine engine = new BlueprintEngine(registry, tree, new ExpressionEngine());
        BlueprintDefinition autoMixin = new BlueprintDefinition(
                "auto-mix-1",
                "auto-mix",
                "",
                BlueprintType.MIXIN,
                ObjectType.DEVICE,
                "self.online.value == true",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
        );
        engine.createBlueprint(autoMixin);

        TypedBlueprintFacade facade = new TypedBlueprintFacade(
                BlueprintType.MIXIN,
                registry,
                engine,
                mock(BlueprintPersistenceService.class),
                mock(BlueprintApplicationService.class),
                mock(ObjectManager.class),
                null
        );

        assertThatThrownBy(() -> facade.apply(autoMixin.id(), "root.platform.devices.dev-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("suitabilityExpression");
        assertThatThrownBy(() -> facade.detach(autoMixin.id(), "root.platform.devices.dev-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("suitabilityExpression");
    }

    @Test
    void bundleValidatorRejectsSqlBindingsOnMixinBlueprint() {
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<ObjectManager> omProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<com.ispf.server.application.bundle.BundleSchemaValidator> schemaProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        ObjectManager objectManager = mock(ObjectManager.class);
        when(omProvider.getIfAvailable()).thenReturn(objectManager);
        when(objectManager.tree()).thenReturn(new ObjectTree());

        BundleManifestValidator validator = new BundleManifestValidator(
                mock(com.ispf.server.application.script.FunctionScriptValidator.class),
                mock(com.ispf.server.application.bundle.BundleDependencyVerifier.class),
                mock(com.ispf.server.license.CommercialBundleLicenseVerifier.class),
                mock(com.ispf.server.application.bundle.ApplicationBundleSnapshotStore.class),
                new tools.jackson.databind.ObjectMapper(),
                omProvider,
                schemaProvider
        );

        ApplicationBundleDeployService.BundleBlueprint badMixin = new ApplicationBundleDeployService.BundleBlueprint(
                "bad-mixin",
                "",
                BlueprintType.MIXIN,
                ObjectType.CUSTOM,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new BlueprintSqlBindingTemplate("v", "SELECT 1 AS value", "root.platform.data-sources.app", 1000L, "value")),
                List.of(),
                Map.of()
        );
        ApplicationBundleDeployService.BundleManifest manifest = new ApplicationBundleDeployService.BundleManifest(
                "1.0.0",
                "Test App",
                "ta_",
                "app_test",
                List.of(),
                List.of(),
                List.of(),
                List.of(badMixin),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );

        BundleValidationResult result = validator.validate("test-app", manifest, false);

        assertThat(result.issues())
                .anyMatch(issue -> "MIXIN_SQL_BINDINGS_FORBIDDEN".equals(issue.code()));
    }
}
