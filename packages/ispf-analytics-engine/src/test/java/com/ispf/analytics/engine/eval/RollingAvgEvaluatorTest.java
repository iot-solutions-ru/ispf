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

class RollingAvgEvaluatorTest {

    @Test
    void emptyWindowDoesNotReturnTheLiveValue() {
        AnalyticsEvaluationResult result = new RollingAvgEvaluator().evaluate(
                tag(),
                new EmptyHistorian(),
                new FixedLivePort(-20.0),
                new AnalyticsEvaluationOptions(Instant.parse("2020-01-01T00:00:00Z"))
        );

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.outputs()).isEmpty();
        assertThat(result.message()).isEqualTo("No historian buckets for avg");
    }

    @Test
    void returnsTheLatestBucketWhenHistoryExists() {
        AnalyticsEvaluationResult result = new RollingAvgEvaluator().evaluate(
                tag(),
                new LatestBucketHistorian(-20.5),
                new FixedLivePort(-20.0),
                AnalyticsEvaluationOptions.now()
        );

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.outputs()).containsEntry("avgTemperature5m", -20.5);
    }

    @Test
    void nearNowEmptyWindowMayUseLiveForChainBootstrap() {
        AnalyticsEvaluationResult result = new RollingAvgEvaluator().evaluate(
                tag(),
                new EmptyHistorian(),
                new FixedLivePort(-20.0),
                AnalyticsEvaluationOptions.now()
        );

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.outputs()).containsEntry("avgTemperature5m", -20.0);
    }

    @Test
    void futureAsOfEmptyWindowDoesNotReturnTheLiveValue() {
        AnalyticsEvaluationResult result = new RollingAvgEvaluator().evaluate(
                tag(),
                new EmptyHistorian(),
                new FixedLivePort(-20.0),
                new AnalyticsEvaluationOptions(Instant.parse("2099-01-01T00:00:00Z"))
        );

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.outputs()).isEmpty();
    }

    private static AnalyticsTagDefinition tag() {
        return new AnalyticsTagDefinition(
                "root.room/tag/avg",
                "avg",
                List.of(new AnalyticsSourceRef("root.room", "temperature", "value")),
                "5m",
                60_000L,
                false,
                true,
                "avgTemperature5m"
        );
    }

    private static final class FixedLivePort implements LiveVariablePort {
        private final double value;

        private FixedLivePort(double value) {
            this.value = value;
        }

        @Override
        public Optional<Double> readNumeric(String objectPath, String variableName, String fieldName) {
            return Optional.of(value);
        }
    }

    private static final class EmptyHistorian implements HistorianPort {
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
            return List.of();
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

    private static final class LatestBucketHistorian implements HistorianPort {
        private final double avg;

        private LatestBucketHistorian(double avg) {
            this.avg = avg;
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
            return List.of(new HistorianBucket(to, avg, avg, avg, 2));
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
}
