package com.ispf.analytics.engine.eval;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SumEvaluatorTest {

    private static final Instant AS_OF = Instant.parse("2026-11-18T07:04:00Z");

    @Test
    void sumsBucketAveragesInsideTheExpressionWindow() {
        RecordingHistorian historian = new RecordingHistorian();
        AnalyticsEvaluationResult result = new SumEvaluator().evaluate(
                tag(),
                historian,
                new EmptyLivePort(),
                new AnalyticsEvaluationOptions(AS_OF)
        );

        assertThat(historian.lastFrom).isEqualTo(AS_OF.minus(Duration.ofMinutes(5)));
        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.outputs()).containsEntry("sumTemperature", 4.0);
    }

    private static AnalyticsTagDefinition tag() {
        return new AnalyticsTagDefinition(
                "root.room/tag/sum",
                "sum",
                List.of(new AnalyticsSourceRef("root.room", "temperature", "value")),
                "5m",
                60_000L,
                false,
                true,
                "sumTemperature"
        );
    }

    private static final class RecordingHistorian implements HistorianPort {
        private Instant lastFrom;

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
            lastFrom = from;
            HistorianBucket recentA = new HistorianBucket(AS_OF.minusSeconds(180), 1.5, 1.0, 2.0, 2);
            HistorianBucket recentB = new HistorianBucket(AS_OF.minusSeconds(60), 2.5, 2.0, 3.0, 2);
            if (Duration.between(from, to).compareTo(Duration.ofHours(1)) > 0) {
                HistorianBucket older = new HistorianBucket(AS_OF.minus(Duration.ofHours(2)), 10.0, 10.0, 10.0, 1);
                return List.of(older, recentA, recentB);
            }
            return List.of(recentA, recentB);
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

    private static final class EmptyLivePort implements LiveVariablePort {
        @Override
        public Optional<Double> readNumeric(String objectPath, String variableName, String fieldName) {
            return Optional.empty();
        }

        @Override
        public void writeNumeric(String objectPath, String variableName, String fieldName, double value) {
        }
    }
}
