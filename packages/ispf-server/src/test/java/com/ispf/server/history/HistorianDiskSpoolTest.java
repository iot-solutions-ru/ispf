package com.ispf.server.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistorianDiskSpoolTest {

    @TempDir
    Path dir;

    @Test
    void restartReplaysOnlyTheUnackedBatch() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, true);
        spool.open();
        String first = spool.append(List.of(sample(1)));
        String second = spool.append(List.of(sample(2)));
        spool.ack(first);
        spool.close();

        HistorianDiskSpool reopened = spool(1024 * 1024, 64 * 1024, true);
        reopened.open();
        List<String> seen = new ArrayList<>();
        assertEquals(1, reopened.replay((id, records) -> seen.add(id + ":" + records.getFirst().valueDouble()), 10));
        assertEquals(List.of(second + ":2.0"), seen);
        assertEquals(0, reopened.replay((id, records) -> seen.add("again"), 10));
        assertEquals(0, reopened.stats().unackedRecords());
        assertEquals(0, reopened.stats().oldestAgeMillis());
        reopened.close();
    }

    @Test
    void tornTailIsDroppedAndTheGoodFrameRemains() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, true);
        spool.open();
        spool.append(List.of(sample(7)));
        spool.close();
        Path segment = onlySegment();
        Files.write(segment, new byte[] {1, 2, 3, 4, 5}, java.nio.file.StandardOpenOption.APPEND);

        HistorianDiskSpool reopened = spool(1024 * 1024, 64 * 1024, true);
        reopened.open();
        List<Double> values = new ArrayList<>();
        assertEquals(1, reopened.replay((id, records) -> values.add(records.getFirst().valueDouble()), 10));
        assertEquals(List.of(7.0), values);
        reopened.close();
    }

    @Test
    void quotaDoesNotDeleteTheAcceptedFrame() throws Exception {
        HistorianDiskSpool spool = spool(350, 64 * 1024, true);
        spool.open();
        spool.append(List.of(sample(1)));
        assertThrows(HistorianSpoolQuotaException.class, () -> spool.append(List.of(sample(2))));
        assertEquals(1, spool.stats().unackedRecords());
        assertTrue(spool.stats().quotaHit());
        spool.close();
        assertTrue(segmentCount() >= 1);
    }

    @Test
    void repeatedReplayDoesNotEmitASecondCopy() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, true);
        spool.open();
        spool.append(List.of(sample(4)));
        CopyOnWriteArrayList<String> delivered = new CopyOnWriteArrayList<>();
        spool.replay((id, records) -> delivered.add(id), 10);
        spool.replay((id, records) -> delivered.add(id), 10);
        assertEquals(1, delivered.size());
        spool.close();
    }

    @Test
    void fsyncLeavesAReadableSegment() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, true);
        spool.open();
        spool.append(List.of(sample(3)));
        spool.close();
        assertTrue(Files.size(onlySegment()) > 20);
    }

    @Test
    void ackedClosedSegmentFreesItsBytes() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 40, true);
        spool.open();
        String first = spool.append(List.of(sample(1)));
        spool.append(List.of(sample(2)));
        long before = spool.stats().bytes();
        spool.ack(first);
        assertTrue(spool.stats().bytes() < before);
        assertEquals(1, segmentCount());
        List<Double> values = new ArrayList<>();
        assertEquals(1, spool.replay((id, records) -> values.add(records.getFirst().valueDouble()), 10));
        assertEquals(List.of(2.0), values);
        spool.close();
    }

    @Test
    void ackClearsLagAndReclaimsAFullAckedSegment() throws Exception {
        HistorianDiskSpool spool = spool(350, 64 * 1024, true);
        spool.open();
        String first = spool.append(List.of(sample(1)));
        assertEquals(1, spool.stats().unackedRecords());
        assertTrue(spool.stats().oldestAgeMillis() >= 0);
        spool.ack(first);
        assertEquals(0, spool.stats().unackedRecords());
        assertEquals(0, spool.stats().oldestAgeMillis());
        spool.append(List.of(sample(2)));
        assertEquals(1, spool.stats().unackedRecords());
        assertEquals(false, spool.stats().quotaHit());
        spool.close();
    }

    @Test
    void claimedAppendIsHiddenFromReplayUntilReleased() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, true);
        spool.open();
        String id = spool.appendClaimed(List.of(sample(9)));
        List<String> seen = new ArrayList<>();
        assertEquals(0, spool.replay((batch, records) -> seen.add(batch), 10));
        spool.release(id);
        assertEquals(1, spool.replay((batch, records) -> seen.add(batch), 10));
        assertEquals(List.of(id), seen);
        spool.close();
    }

    @Test
    void failedReplayReleasesEveryFrameInTheBatch() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, true);
        spool.open();
        spool.append(List.of(sample(1)));
        spool.append(List.of(sample(2)));
        assertEquals(0, spool.replay((id, records) -> {
            throw new IllegalStateException("down");
        }, 10));
        List<String> seen = new ArrayList<>();
        assertEquals(2, spool.replay((id, records) -> seen.add(id), 10));
        assertEquals(2, seen.size());
        spool.close();
    }

    @Test
    void aSmallerBatchClearsQuotaAfterALargerBatchWasRejected() throws Exception {
        HistorianDiskSpool spool = spool(350, 64 * 1024, true);
        spool.open();
        assertThrows(HistorianSpoolQuotaException.class, () -> spool.append(List.of(
                sample(1), sample(2), sample(3), sample(4), sample(5))));
        assertTrue(spool.stats().quotaHit());
        spool.append(List.of(sample(1)));
        assertFalse(spool.stats().quotaHit());
        spool.close();
    }

    @Test
    void smallSegmentsRollToASecondFile() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 40, true);
        spool.open();
        spool.append(List.of(sample(1)));
        spool.append(List.of(sample(2)));
        spool.close();
        long segments = segmentCount();
        assertTrue(segments >= 2, "segments=" + segments);
    }

    private Path onlySegment() throws Exception {
        try (var stream = Files.list(dir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".seg")).findFirst().orElseThrow();
        }
    }

    private long segmentCount() throws Exception {
        try (var stream = Files.list(dir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".seg")).count();
        }
    }

    private HistorianDiskSpool spool(long maxBytes, long segmentBytes, boolean fsync) {
        return new HistorianDiskSpool(dir, maxBytes, segmentBytes, fsync, new ObjectMapper());
    }

    private static VariableHistoryWriteRecord sample(double value) {
        return new VariableHistoryWriteRecord(
                "root.a", "v", "value",
                Instant.parse("2026-11-13T00:00:00Z"),
                Instant.parse("2026-11-13T00:00:00Z"),
                value, null, "GOOD"
        );
    }
}
