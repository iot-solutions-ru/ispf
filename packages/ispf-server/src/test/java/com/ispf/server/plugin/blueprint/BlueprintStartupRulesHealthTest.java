package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlueprintStartupRulesHealthTest {

    private static final String PATH = "root.platform.devices.pump";

    private final ObjectManager objectManager = mock(ObjectManager.class);
    private final ObjectTree tree = mock(ObjectTree.class);
    private final BindingRulesService rules = mock(BindingRulesService.class);
    private final BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth(objectManager, rules);
    private final PlatformObject pump = new PlatformObject("pump", PATH, ObjectType.CUSTOM, "Pump", "", "pump-type");

    @BeforeEach
    void setUp() {
        when(objectManager.tree()).thenReturn(tree);
        when(tree.findByPath(PATH)).thenReturn(Optional.of(pump));
        pump.setappliedBlueprintIds(List.of("pump-type", "alarm-mixin"));
    }

    @Test
    void staysDownWhileSavedRuleIsStillUnresolved() {
        when(rules.listRules(PATH)).thenReturn(List.of(limitRule("${parentPath}/limit")));
        health.reportAll(List.of(problem("alarm-mixin", BlueprintStartupRulesHealth.Kind.UNRESOLVED)));

        assertThat(health.isReady()).isFalse();
        assertThat(health.problems()).containsExactly(PATH + "#limit: saved instance rule still contains ${...}");
    }

    @Test
    void recoversWhenSavedRuleIsResolvedAtRuntime() {
        when(rules.listRules(PATH)).thenReturn(List.of(limitRule("${parentPath}/limit")));
        health.reportAll(List.of(problem("alarm-mixin", BlueprintStartupRulesHealth.Kind.UNRESOLVED)));
        assertThat(health.isReady()).isFalse();

        when(rules.listRules(PATH)).thenReturn(List.of(limitRule("root.platform.devices/limit")));

        assertThat(health.isReady()).isTrue();
        when(rules.listRules(PATH)).thenReturn(List.of(limitRule("${parentPath}/limit")));
        assertThat(health.isReady()).isTrue();
    }

    @Test
    void recoversWhenMissingRuleIsSavedAtRuntime() {
        when(rules.listRules(PATH)).thenReturn(List.of());
        health.reportAll(List.of(problem("pump-type", BlueprintStartupRulesHealth.Kind.MISSING)));
        assertThat(health.isReady()).isFalse();

        when(rules.listRules(PATH)).thenReturn(List.of(limitRule("root.platform.devices/limit")));

        assertThat(health.isReady()).isTrue();
    }

    @Test
    void recoversWhenObjectIsDeleted() {
        when(rules.listRules(PATH)).thenReturn(List.of());
        health.reportAll(List.of(problem("pump-type", BlueprintStartupRulesHealth.Kind.MISSING)));
        assertThat(health.isReady()).isFalse();

        when(tree.findByPath(PATH)).thenReturn(Optional.empty());

        assertThat(health.isReady()).isTrue();
    }

    @Test
    void recoversWhenMixinIsDetached() {
        when(rules.listRules(PATH)).thenReturn(List.of());
        health.reportAll(List.of(problem("alarm-mixin", BlueprintStartupRulesHealth.Kind.MISSING)));
        assertThat(health.isReady()).isFalse();

        pump.removeAppliedBlueprintId("alarm-mixin");

        assertThat(health.isReady()).isTrue();
    }

    @Test
    void reportsMissingOnceUnresolvedRuleIsDeletedButBlueprintStaysApplied() {
        when(rules.listRules(PATH)).thenReturn(List.of(limitRule("${parentPath}/limit")));
        health.reportAll(List.of(problem("alarm-mixin", BlueprintStartupRulesHealth.Kind.UNRESOLVED)));

        when(rules.listRules(PATH)).thenReturn(List.of());

        assertThat(health.problems())
                .containsExactly(PATH + "#limit: templated blueprint rule has no saved instance rule");
    }

    @Test
    void staysDownWhenLiveRulesCannotBeRead() {
        when(rules.listRules(PATH)).thenThrow(new IllegalStateException("database unavailable"));
        health.reportAll(List.of(problem("pump-type", BlueprintStartupRulesHealth.Kind.MISSING)));

        assertThat(health.isReady()).isFalse();
    }

    @Test
    void resetClearsFindings() {
        when(rules.listRules(PATH)).thenReturn(List.of());
        health.reportAll(List.of(problem("pump-type", BlueprintStartupRulesHealth.Kind.MISSING)));

        health.reset();

        assertThat(health.isReady()).isTrue();
    }

    private static BlueprintStartupRulesHealth.Problem problem(String blueprintId, BlueprintStartupRulesHealth.Kind kind) {
        return new BlueprintStartupRulesHealth.Problem(PATH, "limit", blueprintId, blueprintId, kind);
    }

    private static BindingRule limitRule(String targetRef) {
        return new BindingRule(
                "limit",
                "limit",
                true,
                0,
                null,
                "",
                "1",
                new BindingTarget("variable", "limit", "value", null, null, targetRef)
        );
    }
}
