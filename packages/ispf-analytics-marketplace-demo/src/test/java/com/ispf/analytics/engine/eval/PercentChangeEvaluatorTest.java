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

class PercentChangeEvaluatorTest {

    @Test
    void computesPercentChangeFromFirstAndLastBucketAverage() {
        PercentChangeEvaluator evaluator = new PercentChangeEvaluator();
        AnalyticsTagDefinition tag = tagDefinition();
        HistorianPort historian = new StubHistorian(
                List.of(
                        new HistorianPort.HistorianBucket(Instant.parse("2026-07-10T00:00:00Z"), 100.0, 100.0, 100.0, 1),
                        new HistorianPort.HistorianBucket(Instant.parse("2026-07-10T01:00:00Z"), 125.0, 125.0, 125.0, 1)
                )
        );

        AnalyticsEvaluationResult result = evaluator.evaluate(
                tag,
                historian,
                new NoopLivePort(),
                new AnalyticsEvaluationOptions(Instant.parse("2026-07-10T02:00:00Z"))
        );

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.outputs()).containsEntry("derivedValue", 25.0);
    }

    @Test
    void skipsWhenBaselineAverageIsZero() {
        PercentChangeEvaluator evaluator = new PercentChangeEvaluator();
        HistorianPort historian = new StubHistorian(
                List.of(
                        new HistorianPort.HistorianBucket(Instant.parse("2026-07-10T00:00:00Z"), 0.0, 0.0, 0.0, 1),
                        new HistorianPort.HistorianBucket(Instant.parse("2026-07-10T01:00:00Z"), 10.0, 10.0, 10.0, 1)
                )
        );

        AnalyticsEvaluationResult result = evaluator.evaluate(
                tagDefinition(),
                historian,
                new NoopLivePort(),
                new AnalyticsEvaluationOptions(Instant.parse("2026-07-10T02:00:00Z"))
        );

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.message()).contains("zero");
    }

    private static AnalyticsTagDefinition tagDefinition() {
        return new AnalyticsTagDefinition(
                "root.site.level.pct-change",
                "percentChange",
                List.of(new AnalyticsSourceRef("root.site.tank-1", "level", "value")),
                "1h",
                60_000L,
                true,
                true,
                "derivedValue"
        );
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
