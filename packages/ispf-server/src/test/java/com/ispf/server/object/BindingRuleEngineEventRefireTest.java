package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingRuleEngineEventRefireTest {

    private static final String PATH = "root.platform.devices.event-refire-probe";
    private static final String EVENT = "doorOpenRiskStarted";

    @Test
    void oneTrueConditionEmitsTheEventOnce() {
        EventService events = mock(EventService.class);
        BindingRuleEngine engine = engine(events, List.of(eventRule()));

        engine.runRulesForObject(PATH);

        verify(events, times(1)).fire(eq(PATH), eq(EVENT), any(DataRecord.class));
    }

    @Test
    void laterPassesOfTheSameChainDoNotEmitTheEventAgain() {
        EventService events = mock(EventService.class);
        BindingExpressionEvaluator evaluator = mock(BindingExpressionEvaluator.class);
        when(evaluator.evaluate(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(DataRecord.single(
                        DataSchema.builder("bindingActionResult").field("value", FieldType.STRING).build(),
                        java.util.Map.of("value", "ok")
                )));
        BindingRuleEngine engine = engine(events, evaluator, List.of(eventRule(), actionRule()));

        assertThatThrownBy(() -> engine.runRulesForObject(PATH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pass limit");
        verify(events, times(1)).fire(eq(PATH), eq(EVENT), any(DataRecord.class));
    }

    private static BindingRule eventRule() {
        return new BindingRule(
                "cc-door-open-event",
                "door",
                true,
                0,
                new BindingActivators(false, List.of(), null, 0),
                "",
                "true",
                new BindingTarget("event", null, "value", null, EVENT)
        );
    }

    private static BindingRule actionRule() {
        return new BindingRule(
                "keep-passing",
                "action",
                true,
                1,
                new BindingActivators(false, List.of(), null, 0),
                "",
                "true",
                new BindingTarget("action", null, "value", null, null)
        );
    }

    private static BindingRuleEngine engine(EventService events, List<BindingRule> rules) {
        return engine(events, mock(BindingExpressionEvaluator.class), rules);
    }

    private static BindingRuleEngine engine(
            EventService events,
            BindingExpressionEvaluator evaluator,
            List<BindingRule> rules
    ) {
        ObjectTree tree = new ObjectTree();
        PlatformObject node = new PlatformObject("event-refire-probe", PATH, ObjectType.DEVICE, "probe", "", null);
        node.addEvent(new EventDescriptor(EVENT, "probe", null, EventLevel.INFO));
        tree.register(node);
        ObjectManager objectManager = mock(ObjectManager.class);
        when(objectManager.tree()).thenReturn(tree);
        when(objectManager.require(PATH)).thenReturn(node);
        BindingRulesService bindingRulesService = mock(BindingRulesService.class);
        when(bindingRulesService.listRules(PATH)).thenReturn(rules);
        ExpressionEngine expressionEngine = mock(ExpressionEngine.class);
        when(expressionEngine.evaluate(any(), any(), any())).thenReturn(Boolean.TRUE);
        return new BindingRuleEngine(
                objectManager,
                bindingRulesService,
                evaluator,
                expressionEngine,
                mock(BindingEvaluationContext.class),
                mock(ObjectChangePublicationService.class),
                mock(BindingRuleAsyncExecutor.class),
                mock(BindingInvokeAuditService.class),
                mock(ObjectEntityMapper.class),
                new ObjectMapper(),
                events
        );
    }
}
