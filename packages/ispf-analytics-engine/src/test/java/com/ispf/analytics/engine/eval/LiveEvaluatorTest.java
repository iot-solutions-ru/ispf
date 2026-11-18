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

class LiveEvaluatorTest {

    @Test
    void returnsTheCurrentNumericValue() {
        AnalyticsEvaluationResult result = new LiveEvaluator().evaluate(
                tag(),
                new EmptyHistorian(),
                new FixedLivePort(-19.4),
                AnalyticsEvaluationOptions.now()
        );

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.outputs()).containsEntry("liveTemperature", -19.4);
    }

    @Test
    void skipsWhenTheLiveValueIsMissing() {
        AnalyticsEvaluationResult result = new LiveEvaluator().evaluate(
                tag(),
                new EmptyHistorian(),
                new FixedLivePort(null),
                AnalyticsEvaluationOptions.now()
        );

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.message()).isEqualTo("No live value");
    }

    private static AnalyticsTagDefinition tag() {
        return new AnalyticsTagDefinition(
                "root.room/tag/live",
                "live",
                List.of(new AnalyticsSourceRef("root.room", "temperature", "value")),
                "5m",
                60_000L,
                false,
                true,
                "liveTemperature"
        );
    }

    private static final class FixedLivePort implements LiveVariablePort {
        private final Double value;

        private FixedLivePort(Double value) {
            this.value = value;
        }

        @Override
        public Optional<Double> readNumeric(String objectPath, String variableName, String fieldName) {
            return Optional.ofNullable(value);
        }

        @Override
        public void writeNumeric(String objectPath, String variableName, String fieldName, double value) {
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
}
