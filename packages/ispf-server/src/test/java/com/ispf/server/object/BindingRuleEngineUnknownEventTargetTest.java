package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.expression.BindingEvaluationContext;
import com.ispf.expression.BindingExpressionEvaluator;
import com.ispf.expression.ExpressionEngine;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.event.EventService;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BindingRuleEngineUnknownEventTargetTest {

    private static final String PATH = "root.platform.devices.event-target-probe";

    @Test
    void unknownEventNameIsARuleFailure() {
        BindingRuleEngine engine = engineWith(new BindingTarget("event", null, "value", null, "missingEvent"));

        assertThatThrownBy(() -> engine.runRulesForObject(PATH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown event: missingEvent on " + PATH);
    }

    @Test
    void nonEventRefIsARuleFailure() {
        BindingRuleEngine engine = engineWith(new BindingTarget("event", null, "value", null, null, "@/temperature"));

        assertThatThrownBy(() -> engine.runRulesForObject(PATH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown event target: @/temperature");
    }

    private static BindingRuleEngine engineWith(BindingTarget target) {
        ObjectTree tree = new ObjectTree();
        PlatformObject node = new PlatformObject("event-target-probe", PATH, ObjectType.DEVICE, "probe", "", null);
        tree.register(node);
        ObjectManager objectManager = mock(ObjectManager.class);
        when(objectManager.tree()).thenReturn(tree);
        when(objectManager.require(PATH)).thenReturn(node);
        BindingRulesService bindingRulesService = mock(BindingRulesService.class);
        when(bindingRulesService.listRules(PATH)).thenReturn(List.of(new BindingRule(
                "rule-event",
                "event",
                true,
                0,
                new BindingActivators(false, List.of(), null, 0),
                "",
                "1",
                target
        )));
        return new BindingRuleEngine(
                objectManager,
                bindingRulesService,
                mock(BindingExpressionEvaluator.class),
                mock(ExpressionEngine.class),
                mock(BindingEvaluationContext.class),
                mock(ObjectChangePublicationService.class),
                mock(BindingRuleAsyncExecutor.class),
                mock(BindingInvokeAuditService.class),
                mock(ObjectEntityMapper.class),
                new ObjectMapper(),
                mock(EventService.class)
        );
    }
}
