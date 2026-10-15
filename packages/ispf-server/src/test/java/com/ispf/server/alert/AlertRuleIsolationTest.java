package com.ispf.server.alert;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.ExpressionEngine;
import com.ispf.expression.ExpressionException;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.event.EventService;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.ml.AnomalyAlertRuleEvaluator;
import com.ispf.server.notification.NotificationDispatchService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.platform.AutomationMetricsRecorder.AlertRuleFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleIsolationTest {

    private static final String TARGET = "root.platform.devices.pump";
    private static final String BROKEN = "root.automation.alert-rules.broken";
    private static final String HEALTHY = "root.automation.alert-rules.healthy";

    @Mock
    AutomationTreeService automationTreeService;
    @Mock
    ObjectManager objectManager;
    @Mock
    ExpressionEngine expressionEngine;
    @Mock
    ExpressionFormalVerificationService formalVerificationService;
    @Mock
    EventService eventService;
    @Mock
    AutomationMetricsRecorder automationMetricsRecorder;
    @Mock
    NotificationDispatchService notificationDispatchService;
    @Mock
    AlarmShelfService alarmShelfService;
    @Mock
    AnomalyAlertRuleEvaluator anomalyAlertRuleEvaluator;

    private AlertRuleService service;
    private PlatformObject target;

    @BeforeEach
    void setUp() {
        service = new AlertRuleService(
                automationTreeService,
                objectManager,
                expressionEngine,
                formalVerificationService,
                eventService,
                automationMetricsRecorder,
                notificationDispatchService,
                alarmShelfService,
                anomalyAlertRuleEvaluator
        );
        target = deviceWithTemperature(90.0);
    }

    @Test
    void failingRuleDoesNotStopTheOtherRulesOfTheVariable() {
        AlertRule broken = rule(BROKEN, "true", null);
        AlertRule healthy = rule(HEALTHY, "true", null);
        when(automationTreeService.findEnabledAlertRules(TARGET, "temperature")).thenReturn(List.of(broken, healthy));
        when(automationTreeService.getAlertRule(BROKEN)).thenThrow(new IllegalStateException("tree is reloading"));
        when(automationTreeService.getAlertRule(HEALTHY)).thenReturn(healthy);
        when(objectManager.require(TARGET)).thenReturn(target);
        when(objectManager.require(HEALTHY)).thenReturn(alertNode(HEALTHY));
        when(expressionEngine.evaluateAlertCondition("true", target, "temperature")).thenReturn(true);

        assertThatCode(() -> service.processVariableChange(TARGET, "temperature")).doesNotThrowAnyException();

        verify(eventService).fireAutomation(eq(HEALTHY), eq("raise"), any());
        verify(automationMetricsRecorder).recordAlertRuleFailure(AlertRuleFailure.ERROR);
        verify(automationTreeService, never()).setAlertRuleEnabled(anyString(), anyBoolean());
    }

    @Test
    void unexpectedFailureDoesNotEscapeThePollEvaluation() {
        AlertRule broken = rule(BROKEN, "true", null);
        when(automationTreeService.getAlertRule(BROKEN)).thenReturn(broken);
        when(objectManager.require(TARGET)).thenThrow(new UnsupportedOperationException("tree is read-only"));

        assertThatCode(() -> service.evaluateRule(broken)).doesNotThrowAnyException();

        verify(automationMetricsRecorder).recordAlertRuleFailure(AlertRuleFailure.ERROR);
        verify(eventService, never()).fireAutomation(anyString(), anyString(), any());
        verify(automationTreeService, never()).setAlertRuleLastConditionMet(anyString(), anyBoolean());
    }

    @Test
    void uncomputableConditionIsCountedAsAConditionFailure() {
        AlertRule broken = rule(BROKEN, "no_such_name", null);
        when(automationTreeService.getAlertRule(BROKEN)).thenReturn(broken);
        when(objectManager.require(TARGET)).thenReturn(target);
        when(expressionEngine.evaluateAlertCondition("no_such_name", target, "temperature"))
                .thenThrow(new ExpressionException("unknown name"));

        service.evaluateRule(broken);

        verify(automationMetricsRecorder).recordAlertRuleFailure(AlertRuleFailure.CONDITION);
        verify(automationMetricsRecorder, never()).recordAlertRuleFailure(AlertRuleFailure.ERROR);
        verify(eventService, never()).fireAutomation(anyString(), anyString(), any());
    }

    @Test
    void concurrentEvaluationsOfOneEdgeRuleRaiseOnce() throws Exception {
        AtomicBoolean lastConditionMet = new AtomicBoolean(false);
        when(automationTreeService.getAlertRule(HEALTHY))
                .thenAnswer(invocation -> rule(HEALTHY, "true", lastConditionMet.get()));
        doAnswer(invocation -> {
            lastConditionMet.set(invocation.getArgument(1));
            return null;
        }).when(automationTreeService).setAlertRuleLastConditionMet(eq(HEALTHY), anyBoolean());
        when(automationTreeService.getAlertRuleLastWatchValue(HEALTHY)).thenReturn(null);
        when(objectManager.require(TARGET)).thenReturn(target);
        when(objectManager.require(HEALTHY)).thenReturn(alertNode(HEALTHY));
        CountDownLatch bothEvaluating = new CountDownLatch(2);
        when(expressionEngine.evaluateAlertCondition("true", target, "temperature")).thenAnswer(invocation -> {
            bothEvaluating.countDown();
            // Unserialized evaluations meet here after both read lastConditionMet=false; serialized ones time out.
            bothEvaluating.await(500, TimeUnit.MILLISECONDS);
            return true;
        });
        AlertRule rule = rule(HEALTHY, "true", false);
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                start.await();
                service.evaluateRule(rule);
                return null;
            });
            Future<?> second = pool.submit(() -> {
                start.await();
                service.evaluateRule(rule);
                return null;
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        verify(eventService, times(1)).fireAutomation(eq(HEALTHY), eq("raise"), any());
    }

    private static AlertRule rule(String id, String conditionExpr, Boolean lastConditionMet) {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        return new AlertRule(
                id,
                "isolation",
                TARGET,
                "temperature",
                conditionExpr,
                "raise",
                null,
                true,
                true,
                0,
                false,
                0,
                "HIGH",
                false,
                null,
                0,
                1000,
                null,
                null,
                lastConditionMet,
                null,
                null,
                null,
                null,
                now,
                now,
                null,
                null,
                null,
                null
        );
    }

    private static PlatformObject alertNode(String id) {
        PlatformObject node = new PlatformObject(id, id, ObjectType.ALERT, "isolation", "", null);
        node.addEvent(new EventDescriptor("raise", "raise", DataSchema.builder("payload").build(), EventLevel.WARNING));
        return node;
    }

    private static PlatformObject deviceWithTemperature(double value) {
        PlatformObject node = new PlatformObject(TARGET, TARGET, ObjectType.DEVICE, "pump", "", null);
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
        DataRecord initial = DataRecord.single(schema, Map.of("value", value));
        node.addVariable(new Variable("temperature", schema, true, true, initial));
        return node;
    }
}
