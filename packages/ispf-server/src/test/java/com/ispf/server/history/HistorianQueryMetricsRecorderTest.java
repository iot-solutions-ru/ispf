package com.ispf.server.history;

import com.ispf.server.config.VariableHistorySloProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HistorianQueryMetricsRecorderTest {

    @Test
    void computesP50AndP95ForAggregateQueries() {
        HistorianQueryMetricsRecorder recorder = new HistorianQueryMetricsRecorder(Optional.empty());
        recorder.recordAggregateQuery(10);
        recorder.recordAggregateQuery(20);
        recorder.recordAggregateQuery(30);
        recorder.recordAggregateQuery(100);
        recorder.recordAggregateQuery(200);

        VariableHistorySloProperties slo = new VariableHistorySloProperties();
        slo.setAggregateMaxLatencyMs(2_000);
        slo.setAggregateMaxPoints(1_000_000);

        HistorianQueryMetricsRecorder.HistorianQuerySlaSnapshot snapshot = recorder.snapshot(slo);

        assertThat(snapshot.aggregate().p50LatencyMs()).isEqualTo(30);
        assertThat(snapshot.aggregate().p95LatencyMs()).isEqualTo(200);
        assertThat(snapshot.aggregate().sloMet()).isTrue();
        assertThat(snapshot.aggregate().totalQueries()).isEqualTo(5);
    }

    @Test
    void flagsSloViolationWhenP95ExceedsTarget() {
        HistorianQueryMetricsRecorder recorder = new HistorianQueryMetricsRecorder(Optional.empty());
        for (long latency : List.of(100L, 200L, 300L, 400L, 5_000L)) {
            recorder.recordAggregateQuery(latency);
        }

        VariableHistorySloProperties slo = new VariableHistorySloProperties();
        slo.setAggregateMaxLatencyMs(2_000);

        assertThat(recorder.snapshot(slo).aggregate().sloMet()).isFalse();
    }

    @Test
    void percentileHelperHandlesEmptyWindow() {
        assertThat(HistorianQueryMetricsRecorder.percentile(List.of(), 0.95)).isZero();
    }
}
