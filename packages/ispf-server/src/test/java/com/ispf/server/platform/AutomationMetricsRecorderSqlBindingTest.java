package com.ispf.server.platform;

import com.ispf.server.binding.SqlBindingValues;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationMetricsRecorderSqlBindingTest {

    private static final String METRIC = "ispf.sql_binding.refresh_failures.total";

    @Test
    void sqlBindingFailuresAreCountedPerReason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AutomationMetricsRecorder recorder = new AutomationMetricsRecorder(Optional.of(registry));

        recorder.recordSqlBindingFailure(SqlBindingValues.Failure.NO_DATA);
        recorder.recordSqlBindingFailure(SqlBindingValues.Failure.NO_DATA);
        recorder.recordSqlBindingFailure(SqlBindingValues.Failure.QUERY_FAILED);

        assertThat(registry.get(METRIC).tag("reason", "no_data").counter().count()).isEqualTo(2.0);
        assertThat(registry.get(METRIC).tag("reason", "query_failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(METRIC).tag("reason", "bad_value").counter().count()).isZero();
        assertThat(recorder.automationSnapshot()).containsEntry("sqlBindingRefreshFailuresTotal", 3L);
    }
}
