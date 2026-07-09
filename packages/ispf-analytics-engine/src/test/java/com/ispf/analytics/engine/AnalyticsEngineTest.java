package com.ispf.analytics.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsEngineTest {

    @Test
    void evaluatesChainInDependencyOrder() {
        RecordingLivePort live = new RecordingLivePort();
        HistorianPort historian = new StubHistorian(42.0);
        AnalyticsEngine engine = AnalyticsEngine.withBuiltins();

        List<AnalyticsTagDefinition> tags = List.of(
                tag("root.c", "root.b"),
                tag("root.b", "root.a"),
                tag("root.a", "root.sensor", "temperature")
        );

        List<AnalyticsEvaluationResult> results = engine.evaluate(tags, historian, live);

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(r -> "ok".equals(r.status()));
        assertThat(live.writeOrder).containsExactly("root.a", "root.b", "root.c");
        assertThat(live.readNumeric("root.c", "derivedValue", "value")).contains(42.0);
    }

    private static AnalyticsTagDefinition tag(String path, String sourcePath) {
        return tag(path, sourcePath, "derivedValue");
    }

    private static AnalyticsTagDefinition tag(String path, String sourcePath, String sourceVariable) {
        return new AnalyticsTagDefinition(
                path,
                "rollingAvg",
                List.of(new AnalyticsSourceRef(sourcePath, sourceVariable, "value")),
                "5m",
                60_000L,
                true,
                true,
                "derivedValue"
        );
    }

    private static final class StubHistorian implements HistorianPort {
        private final double avg;

        StubHistorian(double avg) {
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
            if ("derivedValue".equals(variableName)) {
                return List.of();
            }
            return List.of(new HistorianBucket(Instant.now(), avg, avg, avg, 1));
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
            return List.of(new HistorianSample(Instant.now(), avg, null));
        }
    }

    private static final class RecordingLivePort implements LiveVariablePort {
        private final Map<String, Double> values = new ConcurrentHashMap<>();
        private final List<String> writeOrder = new ArrayList<>();

        @Override
        public Optional<Double> readNumeric(String objectPath, String variableName, String fieldName) {
            return Optional.ofNullable(values.get(key(objectPath, variableName)));
        }

        @Override
        public void writeNumeric(String objectPath, String variableName, String fieldName, double value) {
            values.put(key(objectPath, variableName), value);
            synchronized (writeOrder) {
                writeOrder.add(objectPath);
            }
        }

        private static String key(String objectPath, String variableName) {
            return objectPath + "|" + variableName;
        }
    }
}
