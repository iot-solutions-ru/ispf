package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingDependencyIndexTest {

    private static final String SOURCE = "root.platform.devices.sensor";
    private static final String HUB_A = "root.platform.devices.hub-a";
    private static final String HUB_B = "root.platform.devices.hub-b";
    private static final String VAR = "temperature";

    @Mock
    BindingRulesService bindingRulesService;

    BindingDependencyIndex index;

    @BeforeEach
    void setUp() {
        index = new BindingDependencyIndex(bindingRulesService);
    }

    @Test
    void rebuildOneConsumerDoesNotDropSiblingOnSameSourceKey() {
        when(bindingRulesService.listRules(HUB_A)).thenReturn(List.of(remoteRule("a")));
        when(bindingRulesService.listRules(HUB_B)).thenReturn(List.of(remoteRule("b")));

        index.rebuild(HUB_A);
        index.rebuild(HUB_B);
        assertThat(index.consumers(SOURCE, VAR)).containsExactlyInAnyOrder(HUB_A, HUB_B);

        index.rebuild(HUB_A);

        assertThat(index.consumers(SOURCE, VAR))
                .as("rebuild(A) must not wipe B from the shared source|variable key")
                .containsExactlyInAnyOrder(HUB_A, HUB_B);
    }

    @Test
    void removeObjectDropsOnlyThatConsumer() {
        when(bindingRulesService.listRules(HUB_A)).thenReturn(List.of(remoteRule("a")));
        when(bindingRulesService.listRules(HUB_B)).thenReturn(List.of(remoteRule("b")));

        index.rebuild(HUB_A);
        index.rebuild(HUB_B);

        index.removeObject(HUB_A);

        assertThat(index.consumers(SOURCE, VAR)).containsExactly(HUB_B);
        assertThat(index.hasConsumers(SOURCE, VAR)).isTrue();
    }

    private static BindingRule remoteRule(String id) {
        return new BindingRule(
                id,
                id,
                true,
                10,
                BindingActivators.onRemoteChange(SOURCE, VAR),
                "",
                "read(\"" + SOURCE + "/" + VAR + "\")",
                new BindingTarget("out", "value")
        );
    }
}
