package com.ispf.server.automation;

import com.ispf.server.alert.AlertRule;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.correlator.EventCorrelator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationRuleIndexTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Mock
    private AutomationTreeService treeService;

    private AutomationRuleIndex index;

    @BeforeEach
    void setUp() {
        index = new AutomationRuleIndex(treeService);
    }

    @Test
    void findAlertRulesReturnsOnlyEnabledRulesForTarget() {
        AlertRule enabled = alertRule("rule-1", "root.device", "temperature", true);
        AlertRule disabled = alertRule("rule-2", "root.device", "temperature", false);
        AlertRule otherVariable = alertRule("rule-3", "root.device", "alarmActive", true);

        when(treeService.listAlertRules()).thenReturn(List.of(enabled, disabled, otherVariable));
        when(treeService.listCorrelators()).thenReturn(List.of());
        when(treeService.getAlertRule("rule-1")).thenReturn(enabled);
        when(treeService.getAlertRule("rule-2")).thenReturn(disabled);
        index.rebuild();

        List<AlertRule> found = index.findAlertRules("root.device", "temperature");
        assertEquals(1, found.size());
        assertEquals("rule-1", found.get(0).id());
    }

    @Test
    void findCorrelatorsForEventIndexesPrimarySecondAndChainParts() {
        EventCorrelator countRule = correlator(
                "corr-1",
                CorrelatorPatternType.COUNT,
                "thresholdExceeded",
                null,
                true
        );
        EventCorrelator sequenceRule = correlator(
                "corr-2",
                CorrelatorPatternType.SEQUENCE,
                "thresholdExceeded",
                "alarmActive",
                true
        );
        EventCorrelator chainRule = correlator(
                "corr-3",
                CorrelatorPatternType.EVENT_CHAIN,
                "start",
                "middle, end",
                true
        );
        EventCorrelator disabledChain = correlator(
                "corr-4",
                CorrelatorPatternType.EVENT_CHAIN,
                "start",
                "middle",
                false
        );

        when(treeService.listAlertRules()).thenReturn(List.of());
        when(treeService.listCorrelators()).thenReturn(List.of(countRule, sequenceRule, chainRule, disabledChain));
        when(treeService.getCorrelator("corr-1")).thenReturn(countRule);
        when(treeService.getCorrelator("corr-2")).thenReturn(sequenceRule);
        when(treeService.getCorrelator("corr-3")).thenReturn(chainRule);
        when(treeService.getCorrelator("corr-4")).thenReturn(disabledChain);
        index.rebuild();

        assertEquals(2, index.findCorrelatorsForEvent("thresholdExceeded").size());
        assertTrue(index.findCorrelatorsForEvent("thresholdExceeded").stream()
                .anyMatch(c -> "corr-1".equals(c.id())));
        assertTrue(index.findCorrelatorsForEvent("thresholdExceeded").stream()
                .anyMatch(c -> "corr-2".equals(c.id())));

        List<EventCorrelator> alarmActive = index.findCorrelatorsForEvent("alarmActive");
        assertEquals(1, alarmActive.size());
        assertEquals("corr-2", alarmActive.get(0).id());

        assertEquals(1, index.findCorrelatorsForEvent("start").size());
        assertEquals(1, index.findCorrelatorsForEvent("middle").size());
        assertEquals(1, index.findCorrelatorsForEvent("end").size());
        assertEquals(1, index.findCorrelatorsForEvent("middle, end").size());
        assertTrue(index.findCorrelatorsForEvent("middle").stream()
                .noneMatch(c -> "corr-4".equals(c.id())));
    }

    @Test
    void invalidateClearsLookupsUntilRebuild() {
        AlertRule rule = alertRule("rule-1", "root.device", "temperature", true);
        when(treeService.listAlertRules()).thenReturn(List.of(rule));
        when(treeService.listCorrelators()).thenReturn(List.of());
        when(treeService.getAlertRule("rule-1")).thenReturn(rule);
        index.rebuild();
        assertEquals(1, index.findAlertRules("root.device", "temperature").size());

        index.invalidate();
        assertTrue(index.findAlertRules("root.device", "temperature").isEmpty());

        index.rebuild();
        assertEquals(1, index.findAlertRules("root.device", "temperature").size());
    }

    @Test
    void addAndRemoveAlertRuleUpdatesIndexWithoutFullRebuild() {
        AlertRule rule = alertRule("rule-1", "root.device", "temperature", true);
        when(treeService.getAlertRule("rule-1")).thenReturn(rule);

        index.addAlertRule(rule);
        assertEquals(1, index.alertRulesIndexed());
        assertEquals(1, index.findAlertRules("root.device", "temperature").size());

        index.removeAlertRule("rule-1");
        assertEquals(0, index.alertRulesIndexed());
        assertTrue(index.findAlertRules("root.device", "temperature").isEmpty());
    }

    @Test
    void updateAlertRuleMovesIndexEntryWhenWatchTargetChanges() {
        AlertRule original = alertRule("rule-1", "root.device", "temperature", true);
        AlertRule moved = alertRule("rule-1", "root.device", "alarmActive", true);
        when(treeService.getAlertRule("rule-1")).thenReturn(moved);

        index.addAlertRule(original);
        index.updateAlertRule(original, moved);

        assertTrue(index.findAlertRules("root.device", "temperature").isEmpty());
        assertEquals(1, index.findAlertRules("root.device", "alarmActive").size());
    }

    private static AlertRule alertRule(String id, String objectPath, String watchVariable, boolean enabled) {
        return new AlertRule(
                id,
                "Rule " + id,
                objectPath,
                watchVariable,
                "true",
                "event",
                null,
                enabled,
                true,
                0,
                false,
                0,
                null,
                null,
                null,
                NOW,
                NOW,
                null,
                null
        );
    }

    private static EventCorrelator correlator(
            String id,
            CorrelatorPatternType patternType,
            String eventName,
            String secondEventName,
            boolean enabled
    ) {
        return new EventCorrelator(
                id,
                "Correlator " + id,
                "root.device",
                patternType,
                eventName,
                secondEventName,
                60,
                1,
                120,
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.demo",
                null,
                enabled,
                null,
                NOW,
                NOW
        );
    }
}
