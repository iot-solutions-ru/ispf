package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.server.config.ObjectChangeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BindingPropagationIsolationTest {

    private static final String SENSOR = "root.platform.devices.sensor-1";
    private static final String GHOST_HUB = "root.platform.singleton-blueprints.deleted-hub";
    private static final String LIVE_HUB = "root.platform.singleton-blueprints.live-hub";

    @Mock
    private BindingRuleEngine bindingRuleEngine;
    @Mock
    private BindingRulesService bindingRulesService;
    @Mock
    private ObjectManager objectManager;

    private BindingDependencyIndex dependencyIndex;

    @BeforeEach
    void setUp() {
        dependencyIndex = new BindingDependencyIndex(bindingRulesService);
    }

    @Test
    void syncPropagationEvictsDeletedConsumerAndStillNotifiesSibling() {
        when(bindingRulesService.listRules(GHOST_HUB)).thenReturn(List.of(remoteRule("ghost")));
        when(bindingRulesService.listRules(LIVE_HUB)).thenReturn(List.of(remoteRule("live")));
        dependencyIndex.rebuild(GHOST_HUB);
        dependencyIndex.rebuild(LIVE_HUB);

        doThrow(new ObjectNotFoundException(GHOST_HUB))
                .when(bindingRuleEngine).onVariableChanged(GHOST_HUB, SENSOR, "temperature");

        BindingPropagationAsyncHandler.propagateVariableChange(
                SENSOR,
                "temperature",
                bindingRuleEngine,
                dependencyIndex
        );

        verify(bindingRuleEngine).onVariableChanged(LIVE_HUB, SENSOR, "temperature");
        assertThat(dependencyIndex.consumers(SENSOR, "temperature")).containsExactly(LIVE_HUB);
    }

    @Test
    void syncPropagationPreservesStopLossException() {
        when(bindingRulesService.listRules(LIVE_HUB)).thenReturn(List.of(remoteRule("live")));
        dependencyIndex.rebuild(LIVE_HUB);

        doThrow(new IllegalStateException("Binding rule chain truncated at " + LIVE_HUB + ": depth limit 16"))
                .when(bindingRuleEngine).onVariableChanged(LIVE_HUB, SENSOR, "temperature");

        assertThatThrownBy(() -> BindingPropagationAsyncHandler.propagateVariableChange(
                SENSOR,
                "temperature",
                bindingRuleEngine,
                dependencyIndex
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("truncated");
    }

    @Test
    void asyncPropagationIsolatesBrokenConsumerSoSiblingStillRuns() {
        ObjectChangeProperties props = new ObjectChangeProperties();
        props.setDemandDrivenPublication(true);
        when(objectManager.isInitialized()).thenReturn(true);
        when(bindingRulesService.listRules(GHOST_HUB)).thenReturn(List.of(remoteRule("broken")));
        when(bindingRulesService.listRules(LIVE_HUB)).thenReturn(List.of(remoteRule("live")));
        dependencyIndex.rebuild(GHOST_HUB);
        dependencyIndex.rebuild(LIVE_HUB);

        doThrow(new RuntimeException("division by zero"))
                .when(bindingRuleEngine).onVariableChanged(GHOST_HUB, SENSOR, "temperature");

        BindingPropagationAsyncHandler handler = new BindingPropagationAsyncHandler(
                bindingRuleEngine,
                dependencyIndex,
                props,
                objectManager
        );
        handler.handle(ObjectChangeEvent.variableUpdated(SENSOR, "temperature"));

        verify(bindingRuleEngine).onVariableChanged(LIVE_HUB, SENSOR, "temperature");
    }

    private static BindingRule remoteRule(String id) {
        return new BindingRule(
                id,
                id,
                true,
                10,
                BindingActivators.onRemoteChange(SENSOR, "temperature"),
                "",
                "read(\"" + SENSOR + "/temperature\")",
                new BindingTarget("out", "value")
        );
    }
}
