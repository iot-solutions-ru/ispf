package com.ispf.server.platform;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationMetricsRecorderAlertRuleTest {

    private static final String METRIC = "ispf.alert.rule_failures.total";

    @Test
    void alertRuleFailuresAreCountedPerReason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AutomationMetricsRecorder recorder = new AutomationMetricsRecorder(Optional.of(registry));

        recorder.recordAlertRuleFailure(AutomationMetricsRecorder.AlertRuleFailure.ERROR);
        recorder.recordAlertRuleFailure(AutomationMetricsRecorder.AlertRuleFailure.ERROR);
        recorder.recordAlertRuleFailure(AutomationMetricsRecorder.AlertRuleFailure.CONDITION);

        assertThat(registry.get(METRIC).tag("reason", "error").counter().count()).isEqualTo(2.0);
        assertThat(registry.get(METRIC).tag("reason", "condition").counter().count()).isEqualTo(1.0);
        assertThat(recorder.automationSnapshot()).containsEntry("alertRuleFailuresTotal", 3L);
    }
}
