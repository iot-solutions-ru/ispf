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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistorianRangeCatchUpServiceTest {

    @TempDir
    Path dir;

    @Test
    void walDirectoryFallsBackUnderDataDir() {
        assertEquals(
                Path.of("custom", "catchup"),
                HistorianRangeCatchUpService.resolveWalDirectory("custom/catchup", "")
        );
        assertEquals(
                Path.of("legacy"),
                HistorianRangeCatchUpService.resolveWalDirectory("", "legacy")
        );
    }

    @Test
    void replayFailureKeepsWalAndRanges() {
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
        doThrow(new RuntimeException("clickhouse down")).when(writer).appendBatch(List.of(sample));

        assertEquals(0, service.replay());
        assertEquals(1, wal.readAll().size());
        assertEquals(1, ranges.list().size());
    }

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
    void fragmentedRangesReplayAsOneBatch() {
        MemoryRanges ranges = new MemoryRanges();
        HistorianSampleWal wal = new HistorianSampleWal(dir, 8_000_000);
        @SuppressWarnings("unchecked")
        ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary = mock(ObjectProvider.class);
        ClickHouseVariableHistorySecondaryWriteStore writer = mock(ClickHouseVariableHistorySecondaryWriteStore.class);
        when(secondary.getIfAvailable()).thenReturn(writer);
        HistorianRangeCatchUpService service = new HistorianRangeCatchUpService(wal, ranges, secondary);
        Instant start = Instant.parse("2026-11-12T10:00:00Z");
        for (int i = 0; i < 12; i++) {
            Instant at = start.plusSeconds(i);
            service.noteSecondaryFailure(List.of(new VariableHistoryWriteRecord(
                    "root.platform.devices.line", "temp", "value", at, at, (double) i, null, "GOOD")));
        }
        assertEquals(12, ranges.list().size());

        int copied = service.replay();

        assertEquals(12, copied);
        verify(writer, times(1)).appendBatch(anyList());
        assertEquals(0, ranges.list().size());
        assertEquals(0, wal.readAll().size());
    }

    @Test
    void replayStopsAfterTheFirstFailedChunk() {
        MemoryRanges ranges = new MemoryRanges();
        HistorianSampleWal wal = new HistorianSampleWal(dir, 64_000_000);
        @SuppressWarnings("unchecked")
        ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary = mock(ObjectProvider.class);
        ClickHouseVariableHistorySecondaryWriteStore writer = mock(ClickHouseVariableHistorySecondaryWriteStore.class);
        when(secondary.getIfAvailable()).thenReturn(writer);
        doThrow(new RuntimeException("clickhouse down")).when(writer).appendBatch(anyList());
        HistorianRangeCatchUpService service = new HistorianRangeCatchUpService(wal, ranges, secondary);
        Instant start = Instant.parse("2026-11-12T10:00:00Z");
        int count = HistorianRangeCatchUpService.REPLAY_CHUNK + 1;
        List<VariableHistoryWriteRecord> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instant at = start.plusMillis(i);
            samples.add(new VariableHistoryWriteRecord(
                    "root.platform.devices.line", "temp", "value", at, at, (double) i, null, "GOOD"));
        }
        service.noteSecondaryFailure(samples);

        assertEquals(0, service.replay());
        verify(writer, times(1)).appendBatch(anyList());
        assertEquals(count, wal.readAll().size());
        assertEquals(1, ranges.list().size());
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

    @Test
    void replayDoesNotDeleteRangesThisNodeNeverHeld() {
        MemoryRanges ranges = new MemoryRanges();
        ranges.cover("root.other|temp|value", Instant.parse("2026-11-12T09:00:00Z"), Instant.parse("2026-11-12T09:00:05Z"));
        HistorianSampleWal wal = new HistorianSampleWal(dir, 1_000_000);
        @SuppressWarnings("unchecked")
        ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary = mock(ObjectProvider.class);
        ClickHouseVariableHistorySecondaryWriteStore writer = mock(ClickHouseVariableHistorySecondaryWriteStore.class);
        when(secondary.getIfAvailable()).thenReturn(writer);
        HistorianRangeCatchUpService service = new HistorianRangeCatchUpService(wal, ranges, secondary);
        VariableHistoryWriteRecord local = new VariableHistoryWriteRecord(
                "root.local", "temp", "value",
                Instant.parse("2026-11-12T10:00:00Z"),
                Instant.parse("2026-11-12T10:00:00Z"),
                1.0, null, "GOOD"
        );
        service.noteSecondaryFailure(List.of(local));

        assertEquals(1, service.replay());
        assertEquals(1, ranges.list().size());
        assertEquals("root.other|temp|value", ranges.list().getFirst().seriesKey());
    }

    @Test
    void secondReplicaReplaysAfterAMergedRangeIsCleared() {
        MergingRanges ranges = new MergingRanges();
        HistorianSampleWal firstWal = new HistorianSampleWal(dir.resolve("replica-1"), 1_000_000);
        HistorianSampleWal secondWal = new HistorianSampleWal(dir.resolve("replica-2"), 1_000_000);
        @SuppressWarnings("unchecked")
        ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary = mock(ObjectProvider.class);
        ClickHouseVariableHistorySecondaryWriteStore writer = mock(ClickHouseVariableHistorySecondaryWriteStore.class);
        when(secondary.getIfAvailable()).thenReturn(writer);
        List<Double> copied = new ArrayList<>();
        doAnswer(invocation -> {
            List<VariableHistoryWriteRecord> batch = invocation.getArgument(0);
            for (VariableHistoryWriteRecord record : batch) {
                copied.add(record.valueDouble());
            }
            return null;
        }).when(writer).appendBatch(anyList());
        HistorianRangeCatchUpService first = new HistorianRangeCatchUpService(firstWal, ranges, secondary);
        HistorianRangeCatchUpService second = new HistorianRangeCatchUpService(secondWal, ranges, secondary);
        Instant start = Instant.parse("2026-11-12T10:00:00Z");
        first.noteSecondaryFailure(List.of(sampleAt(start, 1)));
        second.noteSecondaryFailure(List.of(sampleAt(start.plusSeconds(1), 2)));
        assertEquals(1, ranges.list().size());

        assertEquals(1, first.replay());
        assertEquals(1, ranges.list().size());
        ranges.clear();
        assertEquals(1, second.replay());
        assertEquals(List.of(1.0, 2.0), copied);
    }

    private static VariableHistoryWriteRecord sampleAt(Instant at, double value) {
        return new VariableHistoryWriteRecord(
                "root.shared", "temp", "value", at, at, value, null, "GOOD"
        );
    }

    private static final class MergingRanges implements HistorianCatchUpRangeStore {
        private final List<Range> ranges = new ArrayList<>();

        @Override
        public synchronized void cover(String seriesKey, Instant from, Instant to) {
            Instant start = from.isBefore(to) ? from : to;
            Instant end = from.isBefore(to) ? to : from;
            Instant mergedFrom = start;
            Instant mergedTo = end;
            List<Range> kept = new ArrayList<>();
            for (Range range : ranges) {
                boolean sameSeries = range.seriesKey().equals(seriesKey);
                boolean near = !range.to().isBefore(start.minusSeconds(5)) && !range.from().isAfter(end.plusSeconds(5));
                if (sameSeries && near) {
                    if (range.from().isBefore(mergedFrom)) {
                        mergedFrom = range.from();
                    }
                    if (range.to().isAfter(mergedTo)) {
                        mergedTo = range.to();
                    }
                } else {
                    kept.add(range);
                }
            }
            kept.add(new Range(seriesKey, mergedFrom, mergedTo));
            ranges.clear();
            ranges.addAll(kept);
        }

        @Override
        public synchronized void putExact(String seriesKey, Instant from, Instant to) {
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

        synchronized void clear() {
            ranges.clear();
        }
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
