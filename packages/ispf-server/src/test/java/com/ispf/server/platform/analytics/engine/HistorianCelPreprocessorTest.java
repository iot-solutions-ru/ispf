package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.LiveVariablePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistorianCelPreprocessorTest {

    @Test
    void expandsHistAvgAndEvaluatesCelArithmetic() {
        String expression = "avg(root.devices.a/temperature, 5m) * 2.0";
        RecordingHistorian historian = new RecordingHistorian(21.5);
        String expanded = HistorianCelPreprocessor.expand(
                expression,
                historian,
                unusedLive(),
                Instant.parse("2026-07-09T10:00:00Z")
        );
        assertThat(expanded).isEqualTo("21.5 * 2.0");
        assertThat(historian.calls).isEqualTo(1);
    }

    @Test
    void expandsIntegerHistorianValuesAsDoubleLiterals() {
        String expression = "avg(root.devices.a/temperature, 5m) + 5";
        String expanded = HistorianCelPreprocessor.expand(
                expression,
                new RecordingHistorian(21.0),
                unusedLive(),
                Instant.parse("2026-07-09T10:00:00Z")
        );
        assertThat(expanded).isEqualTo("21.0 + 5.0");
    }

    @Test
    void normalizesIntegerLiteralsForDivision() {
        assertThat(HistorianCelPreprocessor.normalizeIntegerLiterals("(22.02 + 22.00) / 2"))
                .isEqualTo("(22.02 + 22.00) / 2.0");
    }

    @Test
    void rejectsDotFormHistorianSource() {
        assertThatThrownBy(() -> HistorianCelPreprocessor.expand(
                "avg(root.platform.devices.analytics-demo.chain-a.derived-a, 5m)",
                new RecordingHistorian(12.0),
                unusedLive(),
                Instant.parse("2026-07-09T10:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slash ref");
    }

    @Test
    void extractsHistorianSources() {
        String expression = """
                avg(root.devices.a/temperature, 5m)
                + max(root.devices.b/pressure, 1h)
                """;
        var sources = HistorianCelPreprocessor.extractSources(expression);
        assertThat(sources).hasSize(2);
        assertThat(sources.get(0).path()).isEqualTo("root.devices.a");
        assertThat(sources.get(1).variable()).isEqualTo("pressure");
    }

    @Test
    void parseArgsSupportsQuotedPaths() {
        assertThat(HistorianCelPreprocessor.parseArgs("'root.a', 'temperature', '5m'"))
                .containsExactly("root.a", "temperature", "5m");
    }

    @Test
    void expandsPlatformRefAvgForm() {
        String expression = "avg(root.devices.a/temperature, 5m) * 2.0";
        String expanded = HistorianCelPreprocessor.expand(
                expression,
                new RecordingHistorian(21.5),
                unusedLive(),
                Instant.parse("2026-07-09T10:00:00Z")
        );
        assertThat(expanded).isEqualTo("21.5 * 2.0");
    }

    private static LiveVariablePort unusedLive() {
        return new LiveVariablePort() {
            @Override
            public Optional<Double> readNumeric(String objectPath, String variableName, String fieldName) {
                return Optional.empty();
            }

            @Override
            public void writeNumeric(String objectPath, String variableName, String fieldName, double value) {
            }
        };
    }

    private static final class RecordingHistorian implements HistorianPort {
        private final double avg;
        private int calls;

        private RecordingHistorian(double avg) {
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
            calls++;
            return List.of(new HistorianBucket(to, avg, avg, avg, 1));
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
