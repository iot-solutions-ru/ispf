package com.ispf.server.alert;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.event.EventService;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.ml.AnomalyAlertRuleEvaluator;
import com.ispf.server.notification.NotificationDispatchService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.expression.ExpressionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleSoftFailTest {

    @Mock
    AlertRuleService alertRuleService;
    @Mock
    AutomationTreeService automationTreeService;
    @Mock
    PlatformLeaderLockService leaderLockService;
    @Mock
    ClusterProperties clusterProperties;
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

    private AlertRulePeriodicScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AlertRulePeriodicScheduler(
                alertRuleService,
                automationTreeService,
                leaderLockService,
                clusterProperties,
                objectManager
        );
    }

    @Test
    void tickSkipsWhenObjectTreeNotReady() {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objectManager.isInitialized()).thenReturn(false);

        scheduler.tick();

        verify(leaderLockService, never()).tryAcquire(any(), any());
        verify(automationTreeService, never()).listEnabledPeriodicAlertRules();
    }

    @Test
    void missingWatchTargetIsSoftFailedAndDisabled() {
        AlertRuleService service = new AlertRuleService(
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
        AlertRule rule = sampleRule("root.automation.alert-rules.orphan", "root.missing.target");
        when(automationTreeService.getAlertRule(rule.id())).thenReturn(rule);
        when(objectManager.require(rule.objectPath()))
                .thenThrow(new ObjectNotFoundException(rule.objectPath()));

        assertThatCode(() -> service.evaluateRule(rule)).doesNotThrowAnyException();
        verify(automationMetricsRecorder, never()).recordAlertEvaluation();
        verify(automationTreeService).setAlertRuleEnabled(rule.id(), false);
    }

    private static AlertRule sampleRule(String id, String objectPath) {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        return new AlertRule(
                id,
                "orphan",
                objectPath,
                "temperature",
                "self.temperature > 80",
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
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                null,
                null,
                null
        );
    }
}
