package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.BindingEvaluationContext;
import com.ispf.expression.BindingExpressionEvaluator;
import com.ispf.expression.BindingStatePort;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.InMemoryBindingStatePort;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.event.EventService;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingRuleEngineEventRefireTest {

    private static final String PATH = "root.platform.devices.door-hub";
    private static final String EVENT = "doorOpenRiskStarted";
    private static final String EVENT_RULE = "door-open-risk";
    private static final String CONDITION = "self.doorOpen.value > 0.0";
    private static final DataSchema DOOR_OPEN = DataSchema.builder("doorOpen")
            .field("value", FieldType.DOUBLE)
            .build();
    private static final DataSchema DOOR_STATE = DataSchema.builder("doorState")
            .field("value", FieldType.STRING)
            .build();
    private static final DataSchema STATE_JSON = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final PlatformObject hub = doorHub();
    private final ObjectManager objectManager = mock(ObjectManager.class);
    private final BindingExpressionEvaluator evaluator = mock(BindingExpressionEvaluator.class);
    private final ObjectChangePublicationService publication = mock(ObjectChangePublicationService.class);
    private final BindingInvokeAuditService audit = mock(BindingInvokeAuditService.class);
    private final EventService events = mock(EventService.class);

    @Test
    void repeatedActivationsWhileTheConditionStaysTrueEmitOnce() {
        BindingRuleEngine engine = engine(new InMemoryBindingStatePort(), eventRule(CONDITION, onAnyLocalChange()));

        engine.onVariableChanged(PATH, PATH, "doorOpen");
        setDoorOpen(2.0);
        engine.onVariableChanged(PATH, PATH, "doorOpen");
        engine.runRulesForObject(PATH);

        verifyEmitted(1);
    }

    @Test
    void conditionTurningFalseReArmsTheRule() {
        BindingRuleEngine engine = engine(new InMemoryBindingStatePort(), eventRule(CONDITION, onAnyLocalChange()));

        engine.onVariableChanged(PATH, PATH, "doorOpen");
        setDoorOpen(0.0);
        engine.onVariableChanged(PATH, PATH, "doorOpen");
        setDoorOpen(1.0);
        engine.onVariableChanged(PATH, PATH, "doorOpen");

        verifyEmitted(2);
    }

    @Test
    void sameObjectCascadeDoesNotEmitTheEventAgain() {
        when(evaluator.evaluate(any(), eq("doorState"), any(), any(), any()))
                .thenReturn(Optional.of(DataRecord.single(DOOR_STATE, Map.of("value", "open"))));
        BindingRuleEngine engine = engine(
                new InMemoryBindingStatePort(),
                doorStateRule(),
                eventRule(CONDITION, onAnyLocalChange())
        );
        doAnswer(invocation -> {
            engine.onVariableChanged(PATH, PATH, invocation.getArgument(1));
            return true;
        }).when(publication).publishVariableChange(eq(PATH), anyString(), any());

        engine.onVariableChanged(PATH, PATH, "doorOpen");

        verify(publication).publishVariableChange(eq(PATH), eq("doorState"), any());
        verifyEmitted(1);
    }

    @Test
    void laterPassesOfTheSameChainDoNotEmitTheEventAgain() {
        when(evaluator.evaluate(any(), eq("_action"), any(), any(), any()))
                .thenReturn(Optional.of(DataRecord.single(DOOR_STATE, Map.of("value", "ok"))));
        BindingRuleEngine engine = engine(
                new InMemoryBindingStatePort(),
                eventRule(CONDITION, noActivators()),
                actionRule()
        );

        assertThatThrownBy(() -> engine.runRulesForObject(PATH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pass limit");
        verifyEmitted(1);
    }

    @Test
    void ruleWithoutConditionEmitsOncePerActivation() {
        when(evaluator.evaluate(any(), eq("_action"), any(), any(), any()))
                .thenReturn(Optional.of(DataRecord.single(DOOR_STATE, Map.of("value", "ok"))));
        BindingRuleEngine engine = engine(
                new InMemoryBindingStatePort(),
                eventRule("", onAnyLocalChange()),
                actionRule()
        );

        engine.onVariableChanged(PATH, PATH, "doorOpen");
        engine.onVariableChanged(PATH, PATH, "doorOpen");
        assertThatThrownBy(() -> engine.runRulesForObject(PATH)).hasMessageContaining("pass limit");

        verifyEmitted(3);
    }

    @Test
    void eventAndTimerActivationsEmitEachTimeTheConditionHolds() {
        BindingActivators activators = new BindingActivators(
                false,
                List.of(BindingVariableRef.localAny()),
                "doorTick",
                1_000
        );
        BindingRuleEngine engine = engine(new InMemoryBindingStatePort(), eventRule(CONDITION, activators));

        engine.onEvent(PATH, "doorTick");
        engine.onEvent(PATH, "doorTick");
        engine.onPeriodic(PATH, EVENT_RULE);
        engine.onVariableChanged(PATH, PATH, "doorOpen");
        setDoorOpen(0.0);
        engine.onEvent(PATH, "doorTick");

        verifyEmitted(3);
    }

    @Test
    void conditionErrorIsAuditedAndDoesNotReArmTheRule() {
        BindingRuleEngine engine = engine(new InMemoryBindingStatePort(), eventRule(CONDITION, onAnyLocalChange()));
        engine.onVariableChanged(PATH, PATH, "doorOpen");
        DataSchema text = DataSchema.builder("doorOpen").field("value", FieldType.STRING).build();
        hub.getVariable("doorOpen").orElseThrow().setComputedValue(DataRecord.single(text, Map.of("value", "jammed")));

        assertThatThrownBy(() -> engine.onVariableChanged(PATH, PATH, "doorOpen"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Binding condition failed");
        setDoorOpen(1.0);
        engine.onVariableChanged(PATH, PATH, "doorOpen");

        verify(audit).recordCel(
                eq(PATH),
                argThat(rule -> EVENT_RULE.equals(rule.id())),
                eq("VARIABLE_CHANGE"),
                eq(false),
                eq(false),
                contains("Binding condition failed"),
                anyLong(),
                isNull()
        );
        verifyEmitted(1);
    }

    @Test
    void failedEmissionLeavesTheRuleArmed() {
        when(events.fire(eq(PATH), eq(EVENT), any(DataRecord.class)))
                .thenThrow(new IllegalArgumentException("payload rejected"))
                .thenReturn(null);
        BindingRuleEngine engine = engine(new InMemoryBindingStatePort(), eventRule(CONDITION, onAnyLocalChange()));

        assertThatThrownBy(() -> engine.onVariableChanged(PATH, PATH, "doorOpen"))
                .hasMessageContaining("payload rejected");
        engine.onVariableChanged(PATH, PATH, "doorOpen");
        engine.onVariableChanged(PATH, PATH, "doorOpen");

        verifyEmitted(2);
    }

    @Test
    void latchIsWrittenToBindingStateOnlyOnEdges() {
        BindingRuleEngine engine = engine(
                new ObjectBindingStatePort(objectManager, new ObjectMapper()),
                eventRule(CONDITION, onAnyLocalChange())
        );

        engine.onVariableChanged(PATH, PATH, "doorOpen");
        engine.onVariableChanged(PATH, PATH, "doorOpen");
        setDoorOpen(0.0);
        engine.onVariableChanged(PATH, PATH, "doorOpen");
        engine.onVariableChanged(PATH, PATH, "doorOpen");

        List<String> writes = bindingStateWrites(2);
        assertThat(writes.get(0)).contains("\"@event:" + EVENT_RULE + "\"").contains("true");
        assertThat(writes.get(1)).contains("\"@event:" + EVENT_RULE + "\"").contains("false");
        verifyEmitted(1);
    }

    @Test
    void latchSurvivesARestart() {
        engine(new ObjectBindingStatePort(objectManager, new ObjectMapper()), eventRule(CONDITION, onAnyLocalChange()))
                .onVariableChanged(PATH, PATH, "doorOpen");
        String persisted = bindingStateWrites(1).get(0);
        hub.addVariable(new Variable(
                BindingStateVariables.BINDING_STATE,
                STATE_JSON,
                true,
                true,
                DataRecord.single(STATE_JSON, Map.of("value", persisted))
        ));

        BindingRuleEngine restarted = engine(
                new ObjectBindingStatePort(objectManager, new ObjectMapper()),
                eventRule(CONDITION, onAnyLocalChange())
        );
        restarted.onVariableChanged(PATH, PATH, "doorOpen");

        verifyEmitted(1);
    }

    private void verifyEmitted(int times) {
        verify(events, times(times)).fire(eq(PATH), eq(EVENT), any(DataRecord.class));
    }

    private List<String> bindingStateWrites(int expected) {
        ArgumentCaptor<DataRecord> written = ArgumentCaptor.forClass(DataRecord.class);
        verify(objectManager, times(expected)).upsertSystemVariable(
                eq(PATH),
                eq(BindingStateVariables.BINDING_STATE),
                any(),
                written.capture()
        );
        return written.getAllValues().stream()
                .map(record -> String.valueOf(record.firstRow().get("value")))
                .toList();
    }

    private void setDoorOpen(double value) {
        hub.getVariable("doorOpen").orElseThrow().setComputedValue(DataRecord.single(DOOR_OPEN, Map.of("value", value)));
    }

    private static BindingActivators onAnyLocalChange() {
        return BindingActivators.onLocalChange();
    }

    private static BindingActivators noActivators() {
        return new BindingActivators(false, List.of(), null, 0);
    }

    private static BindingRule eventRule(String condition, BindingActivators activators) {
        return new BindingRule(
                EVENT_RULE,
                "door open risk",
                true,
                10,
                activators,
                condition,
                "\"door open\"",
                new BindingTarget("event", null, "value", null, EVENT)
        );
    }

    private static BindingRule doorStateRule() {
        return new BindingRule(
                "door-state",
                "door state",
                true,
                0,
                onAnyLocalChange(),
                "",
                "self.doorOpen.value > 0.0 ? \"open\" : \"closed\"",
                new BindingTarget("doorState", "value")
        );
    }

    private static BindingRule actionRule() {
        return new BindingRule(
                "keep-passing",
                "action",
                true,
                20,
                noActivators(),
                "",
                "true",
                new BindingTarget("action", null, "value", null, null)
        );
    }

    private static PlatformObject doorHub() {
        PlatformObject node = new PlatformObject("door-hub", PATH, ObjectType.CUSTOM, "door hub", "", null);
        node.addEvent(new EventDescriptor(EVENT, "door open risk", null, EventLevel.INFO));
        node.addVariable(new Variable("doorOpen", DOOR_OPEN, true, true, DataRecord.single(DOOR_OPEN, Map.of("value", 1.0))));
        node.addVariable(new Variable(
                "doorState",
                DOOR_STATE,
                true,
                true,
                DataRecord.single(DOOR_STATE, Map.of("value", "closed"))
        ));
        return node;
    }

    private BindingRuleEngine engine(BindingStatePort statePort, BindingRule... rules) {
        ObjectTree tree = new ObjectTree();
        tree.register(hub);
        when(objectManager.tree()).thenReturn(tree);
        when(objectManager.require(PATH)).thenReturn(hub);
        BindingRulesService bindingRulesService = mock(BindingRulesService.class);
        when(bindingRulesService.listRules(PATH)).thenReturn(List.of(rules));
        return new BindingRuleEngine(
                objectManager,
                bindingRulesService,
                evaluator,
                new ExpressionEngine(),
                mock(BindingEvaluationContext.class),
                publication,
                mock(BindingRuleAsyncExecutor.class),
                audit,
                mock(ObjectEntityMapper.class),
                new ObjectMapper(),
                events,
                statePort
        );
    }
}
