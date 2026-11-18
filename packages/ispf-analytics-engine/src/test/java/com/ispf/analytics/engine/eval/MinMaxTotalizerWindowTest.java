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

class MinMaxTotalizerWindowTest {

    private static final Instant AS_OF = Instant.parse("2026-11-17T07:44:00Z");
    private static final Instant LAST_BUCKET = Instant.parse("2026-11-17T07:40:00Z");
    private static final Instant OLD_BUCKET = Instant.parse("2026-11-17T04:30:00Z");

    @Test
    void minMaxAndTotalizerUseTheExpressionWindow() {
        RecordingHistorian historian = new RecordingHistorian();
        AnalyticsEvaluationOptions options = new AnalyticsEvaluationOptions(AS_OF);
        LiveVariablePort live = new EmptyLivePort();

        AnalyticsEvaluationResult min = new MinWindowEvaluator().evaluate(tag("min", "minTemperature"), historian, live, options);
        AnalyticsEvaluationResult max = new MaxWindowEvaluator().evaluate(tag("max", "maxTemperature"), historian, live, options);
        AnalyticsEvaluationResult total = new TotalizerEvaluator().evaluate(tag("totalizer", "totalTemperature"), historian, live, options);

        assertThat(historian.lastFrom).isEqualTo(AS_OF.minus(Duration.ofMinutes(5)));
        assertThat(historian.lastTo).isEqualTo(AS_OF);
        assertThat(min.status()).isEqualTo("ok");
        assertThat(min.outputs()).containsEntry("minTemperature", -21.2);
        assertThat(max.outputs()).containsEntry("maxTemperature", -20.5);
        assertThat(total.outputs().get("totalTemperature")).isEqualTo(-20.9 * 3);
    }

    @Test
    void avgStillReadsTheDayAndKeepsTheLatestBucket() {
        RecordingHistorian historian = new RecordingHistorian();
        AnalyticsEvaluationResult avg = new RollingAvgEvaluator().evaluate(
                tag("avg", "avgTemperature"),
                historian,
                new EmptyLivePort(),
                new AnalyticsEvaluationOptions(AS_OF)
        );

        assertThat(historian.lastFrom).isEqualTo(AS_OF.minus(Duration.ofHours(24)));
        assertThat(avg.outputs()).containsEntry("avgTemperature", -20.9);
    }

    private static AnalyticsTagDefinition tag(String helper, String output) {
        return new AnalyticsTagDefinition(
                "root.room/tag/" + helper,
                helper,
                List.of(new AnalyticsSourceRef("root.room", "temperature", "value")),
                "5m",
                60_000L,
                false,
                true,
                output
        );
    }

    private static final class RecordingHistorian implements HistorianPort {
        private Instant lastFrom;
        private Instant lastTo;

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
            lastTo = to;
            HistorianBucket last = new HistorianBucket(LAST_BUCKET, -20.9, -21.2, -20.5, 3);
            if (Duration.between(from, to).compareTo(Duration.ofHours(1)) > 0) {
                HistorianBucket older = new HistorianBucket(OLD_BUCKET, -21.5, -23.6, -19.4, 3);
                return List.of(older, last);
            }
            return List.of(last);
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
