package com.ispf.server.history;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistorianCatchUpMetricsTest {

    @TempDir
    Path tempDir;

    @Test
    void gaugesReflectOpenRangesAfterSecondaryFailure() {
        MemoryRanges ranges = new MemoryRanges();
        HistorianSampleWal wal = new HistorianSampleWal(tempDir, 1_000_000);
        HistorianRangeCatchUpService catchUp =
                new HistorianRangeCatchUpService(wal, ranges, unusedSecondary());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new HistorianCatchUpMetrics(catchUp, provider(registry));

        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        catchUp.noteSecondaryFailure(List.of(sample("root.a", "temp", at, 1.0)));

        assertThat(registry.get("ispf.historian.dual_write.catchup.ranges").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("ispf.historian.dual_write.catchup.oldest_age_ms").gauge().value())
                .isGreaterThan(0.0);
        assertThat(registry.get("ispf.historian.dual_write.catchup.wal_bytes").gauge().value())
                .isGreaterThan(0.0);
    }

    @Test
    void seedSnapshotReflectsPreexistingWalWithoutNewFailure() {
        MemoryRanges ranges = new MemoryRanges();
        HistorianSampleWal wal = new HistorianSampleWal(tempDir, 1_000_000);
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        VariableHistoryWriteRecord sample = sample("root.a", "temp", at, 1.0);
        wal.append(List.of(sample));
        ranges.cover("root.a|temp|value", at, at);

        HistorianRangeCatchUpService catchUp =
                new HistorianRangeCatchUpService(wal, ranges, unusedSecondary());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new HistorianCatchUpMetrics(catchUp, provider(registry));

        catchUp.seedCatchUpSnapshot();

        assertThat(registry.get("ispf.historian.dual_write.catchup.ranges").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("ispf.historian.dual_write.catchup.wal_bytes").gauge().value())
                .isGreaterThan(0.0);
    }

    private static VariableHistoryWriteRecord sample(String path, String name, Instant at, double value) {
        return new VariableHistoryWriteRecord(path, name, "value", at, at, value, null, "GOOD");
    }

    private static ObjectProvider<MeterRegistry> provider(SimpleMeterRegistry registry) {
        return new ObjectProvider<>() {
            @Override
            public SimpleMeterRegistry getObject() {
                return registry;
            }

            @Override
            public SimpleMeterRegistry getIfAvailable() {
                return registry;
            }

            @Override
            public SimpleMeterRegistry getIfUnique() {
                return registry;
            }
        };
    }

    private static ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> unusedSecondary() {
        return new ObjectProvider<>() {
            @Override
            public ClickHouseVariableHistorySecondaryWriteStore getObject() {
                return null;
            }

            @Override
            public ClickHouseVariableHistorySecondaryWriteStore getIfAvailable() {
                return null;
            }

            @Override
            public ClickHouseVariableHistorySecondaryWriteStore getIfUnique() {
                return null;
            }
        };
    }

    private static final class MemoryRanges implements HistorianCatchUpRangeStore {
        private final List<Range> open = new ArrayList<>();

        @Override
        public synchronized void cover(String seriesKey, Instant from, Instant to) {
            open.add(new Range(seriesKey, from, to));
        }

        @Override
        public synchronized void putExact(String seriesKey, Instant from, Instant to) {
            open.add(new Range(seriesKey, from, to));
        }

        @Override
        public synchronized void remove(String seriesKey, Instant from, Instant to) {
            open.removeIf(range ->
                    range.seriesKey().equals(seriesKey)
                            && range.from().equals(from)
                            && range.to().equals(to));
        }

        @Override
        public synchronized List<Range> list() {
            return List.copyOf(open);
        }
    }
}
