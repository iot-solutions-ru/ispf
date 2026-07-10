package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyDeltaEvaluatorTest {

    @Test
    void computesDeltaFromFirstAndLastBucketAverage() {
        EnergyDeltaEvaluator evaluator = new EnergyDeltaEvaluator();
        AnalyticsTagDefinition tag = new AnalyticsTagDefinition(
                "root.site.energy.delta",
                "energyDelta",
                List.of(new AnalyticsSourceRef("root.site.meter-1", "activeEnergy", "value")),
                "5m",
                60_000L,
                true,
                true,
                "derivedValue"
        );
        HistorianPort historian = new StubHistorian(
                List.of(
                        new HistorianPort.HistorianBucket(Instant.parse("2026-07-10T00:00:00Z"), 10.0, 10.0, 10.0, 1),
                        new HistorianPort.HistorianBucket(Instant.parse("2026-07-10T00:05:00Z"), 16.5, 16.0, 17.0, 2)
                )
        );

        AnalyticsEvaluationResult result = evaluator.evaluate(
                tag,
                historian,
                new NoopLivePort(),
                new AnalyticsEvaluationOptions(Instant.parse("2026-07-10T00:10:00Z"))
        );

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.outputs()).containsEntry("derivedValue", 6.5);
    }

    @Test
    void skipsWhenNotEnoughBuckets() {
        EnergyDeltaEvaluator evaluator = new EnergyDeltaEvaluator();
        AnalyticsTagDefinition tag = new AnalyticsTagDefinition(
                "root.site.energy.delta",
                "energyDelta",
                List.of(new AnalyticsSourceRef("root.site.meter-1", "activeEnergy", "value")),
                "5m",
                60_000L,
                true,
                true,
                "derivedValue"
        );

        AnalyticsEvaluationResult result = evaluator.evaluate(
                tag,
                new StubHistorian(List.of(new HistorianPort.HistorianBucket(Instant.parse("2026-07-10T00:00:00Z"), 10.0, 10.0, 10.0, 1))),
                new NoopLivePort(),
                new AnalyticsEvaluationOptions(Instant.parse("2026-07-10T00:10:00Z"))
        );

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.message()).contains("Need at least two buckets");
    }

    private static final class StubHistorian implements HistorianPort {
        private final List<HistorianBucket> buckets;

        private StubHistorian(List<HistorianBucket> buckets) {
            this.buckets = buckets;
        }

        @Override
        public List<HistorianBucket> aggregate(
                String objectPath,
                String variableName,
                String fieldName,
                Instant from,
                Instant to,
                String windowBucket,
                int maxBuckets
        ) {
            return buckets;
        }

        @Override
        public List<HistorianSample> query(
                String objectPath,
                String variableName,
                String fieldName,
                Instant from,
                Instant to,
                int limit
        ) {
            return List.of();
        }
    }

    private static final class NoopLivePort implements LiveVariablePort {
        @Override
        public Optional<Double> readNumeric(String objectPath, String variableName, String fieldName) {
            return Optional.empty();
        }
    }
}
