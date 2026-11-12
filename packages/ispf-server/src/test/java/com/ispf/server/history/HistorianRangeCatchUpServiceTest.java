package com.ispf.server.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistorianRangeCatchUpServiceTest {

    @TempDir
    Path dir;

    @Test
    void outageThenReplayCopiesWalAndClearsRanges() {
        MemoryRanges ranges = new MemoryRanges();
        HistorianSampleWal wal = new HistorianSampleWal(dir, 1_000_000);
        @SuppressWarnings("unchecked")
        ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary = mock(ObjectProvider.class);
        ClickHouseVariableHistorySecondaryWriteStore writer = mock(ClickHouseVariableHistorySecondaryWriteStore.class);
        when(secondary.getIfAvailable()).thenReturn(writer);
        HistorianRangeCatchUpService service = new HistorianRangeCatchUpService(wal, ranges, secondary);

        VariableHistoryWriteRecord sample = new VariableHistoryWriteRecord(
                "root.platform.devices.line",
                "temp",
                "value",
                Instant.parse("2026-11-12T10:00:00Z"),
                Instant.parse("2026-11-12T10:00:00Z"),
                21.5,
                null,
                "GOOD"
        );
        service.noteSecondaryFailure(List.of(sample));
        assertEquals(1, ranges.list().size());
        assertEquals(1, wal.readAll().size());

        int copied = service.replay();
        assertEquals(1, copied);
        verify(writer).appendBatch(List.of(sample));
        assertEquals(0, ranges.list().size());
        assertEquals(0, wal.readAll().size());
    }

    @Test
    void walRoundTripAndQuotaKeepTheTail() throws Exception {
        HistorianSampleWal wal = new HistorianSampleWal(dir, 1024 * 1024);
        List<VariableHistoryWriteRecord> batch = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            batch.add(new VariableHistoryWriteRecord(
                    "root.a", "v", "value",
                    Instant.parse("2026-11-12T10:00:00Z").plusSeconds(i),
                    Instant.parse("2026-11-12T10:00:00Z").plusSeconds(i),
                    (double) i, null, "GOOD"
            ));
        }
        wal.append(batch);
        HistorianSampleWal reopened = new HistorianSampleWal(dir, 4096);
        List<VariableHistoryWriteRecord> kept = reopened.readAll();
        assertTrue(kept.size() < batch.size());
        assertEquals(199d, kept.getLast().valueDouble());
        assertTrue(Files.size(dir.resolve("samples.wal")) <= 4096);
    }

    @Test
    void carriageReturnInValueTextRoundTrips() {
        HistorianSampleWal wal = new HistorianSampleWal(dir, 1024 * 1024);
        wal.append(List.of(new VariableHistoryWriteRecord(
                "root.a", "v", "value",
                Instant.parse("2026-11-12T10:00:00Z"),
                Instant.parse("2026-11-12T10:00:00Z"),
                null, "line\r\nnext", "GOOD"
        )));
        assertEquals("line\r\nnext", wal.readAll().getFirst().valueText());
    }

    @Test
    void oneHundredThousandAppendsStayWithinBatchBudget() {
        HistorianSampleWal wal = new HistorianSampleWal(dir, 64L * 1024 * 1024);
        HistorianIngressCompressor compressor = new HistorianIngressCompressor(0.5, 0);
        List<Long> batchNanos = new CopyOnWriteArrayList<>();
        Instant start = Instant.parse("2026-11-12T00:00:00Z");
        int kept = 0;
        for (int batch = 0; batch < 100; batch++) {
            List<VariableHistoryWriteRecord> records = new ArrayList<>(1_000);
            for (int i = 0; i < 1_000; i++) {
                int n = batch * 1_000 + i;
                double value = n % 10 == 0 ? n : n - (n % 10);
                if (!compressor.accept("series", start.plusMillis(n), value)) {
                    continue;
                }
                records.add(new VariableHistoryWriteRecord(
                        "root.a", "v", "value", start.plusMillis(n), start.plusMillis(n), value, null, "GOOD"));
            }
            long began = System.nanoTime();
            wal.append(records);
            batchNanos.add(System.nanoTime() - began);
            kept += records.size();
        }
        batchNanos.sort(Long::compare);
        long p99 = batchNanos.get((int) Math.ceil(batchNanos.size() * 0.99) - 1);
        assertEquals(true, kept < 100_000);
        assertEquals(true, p99 / 1_000_000L <= 500, "p99 batch append ms=" + (p99 / 1_000_000L));
    }

    private static final class MemoryRanges implements HistorianCatchUpRangeStore {
        private final List<Range> ranges = new ArrayList<>();

        @Override
        public synchronized void cover(String seriesKey, Instant from, Instant to) {
            ranges.add(new Range(seriesKey, from, to));
        }

        @Override
        public synchronized List<Range> list() {
            return List.copyOf(ranges);
        }

        @Override
        public synchronized void remove(String seriesKey, Instant from, Instant to) {
            ranges.removeIf(range -> range.seriesKey().equals(seriesKey)
                    && range.from().equals(from)
                    && range.to().equals(to));
        }
    }
}
