package com.ispf.server.object.pubsub;

import com.ispf.server.application.binding.ApplicationSqlBindingEventIndex;
import com.ispf.server.automation.AutomationRuleIndex;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.workflow.WorkflowEventTriggerIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventFiredSubscriptionRegistryTest {

    private static final String PATH = "root.dev.sensor";
    private static final String EVENT = "thresholdExceeded";

    @Mock
    private BindingDependencyIndex bindingDependencyIndex;

    @Mock
    private AutomationRuleIndex automationRuleIndex;

    @Mock
    private WorkflowEventTriggerIndex workflowTriggerIndex;

    @Mock
    private ApplicationSqlBindingEventIndex sqlBindingEventIndex;

    private EventFiredSubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EventFiredSubscriptionRegistry(
                bindingDependencyIndex,
                automationRuleIndex,
                workflowTriggerIndex,
                sqlBindingEventIndex
        );
    }

    @Test
    void returnsNoneForBlankInput() {
        assertThat(registry.interest("", EVENT)).isEqualTo(EventFiredInterest.NONE);
        assertThat(registry.interest(PATH, "")).isEqualTo(EventFiredInterest.NONE);
    }

    @Test
    void detectsBindingConsumers() {
        when(bindingDependencyIndex.eventConsumers(PATH, EVENT)).thenReturn(Set.of(PATH));

        EventFiredInterest interest = registry.interest(PATH, EVENT);

        assertThat(interest.bindings()).isTrue();
        assertThat(interest.hasAny()).isTrue();
    }

    @Test
    void detectsCorrelators() {
        when(automationRuleIndex.findCorrelatorsForEvent(EVENT)).thenReturn(List.of());

        EventFiredInterest interest = registry.interest(PATH, EVENT);

        assertThat(interest.correlators()).isFalse();
    }

    @Test
    void detectsWorkflowsAndSqlBindings() {
        when(workflowTriggerIndex.findEventWorkflows(PATH, EVENT)).thenReturn(List.of("wf-1"));
        when(sqlBindingEventIndex.hasBindings(PATH, EVENT)).thenReturn(true);

        EventFiredInterest interest = registry.interest(PATH, EVENT);

        assertThat(interest.workflows()).isTrue();
        assertThat(interest.sqlBindings()).isTrue();
    }
}
