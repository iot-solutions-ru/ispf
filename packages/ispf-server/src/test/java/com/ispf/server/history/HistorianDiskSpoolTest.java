package com.ispf.server.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.zip.CRC32;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        List<String> seen = new CopyOnWriteArrayList<>();
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
    void acceptP99UsesTheDurableAppendSamples() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, false);
        spool.open();
        for (int i = 0; i < 100; i++) {
            spool.recordAccept(i);
        }
        assertEquals(98, spool.stats().acceptP99Millis());
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

    @Test
    void outOfOrderAckedAboveSurvivesTailLossOfAckedFrame() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, true);
        spool.open();
        spool.append(List.of(sample(1)));
        String second = spool.append(List.of(sample(2)));
        spool.ack(second);
        truncateAfterFrames(1);
        spool.close();

        HistorianDiskSpool reopened = spool(1024 * 1024, 64 * 1024, true);
        reopened.open();
        String third = reopened.append(List.of(sample(3)));
        assertTrue(third.endsWith("-3"), third);
        List<Double> values = new CopyOnWriteArrayList<>();
        assertEquals(2, reopened.replay((id, records) -> values.add(records.getFirst().valueDouble()), 10));
        assertEquals(Set.of(1.0, 3.0), new HashSet<>(values));
        reopened.close();
    }

    @Test
    void batchIdsCarryAStableSpoolIdAcrossRestart() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, true);
        spool.open();
        String first = spool.append(List.of(sample(1)));
        String spoolId = spool.spoolId();
        assertTrue(first.startsWith(spoolId + "-"));
        spool.close();

        HistorianDiskSpool reopened = spool(1024 * 1024, 64 * 1024, true);
        reopened.open();
        assertEquals(spoolId, reopened.spoolId());
        String second = reopened.append(List.of(sample(2)));
        assertTrue(second.startsWith(spoolId + "-"));
        reopened.close();
    }

    @Test
    void aFreshDirectoryGetsADistinctSpoolId() throws Exception {
        HistorianDiskSpool first = spool(1024 * 1024, 64 * 1024, true);
        first.open();
        String firstId = first.spoolId();
        first.close();

        Path other = dir.resolve("other");
        Files.createDirectories(other);
        HistorianDiskSpool second = new HistorianDiskSpool(other, 1024 * 1024, 64 * 1024, true, new ObjectMapper());
        second.open();
        assertFalse(firstId.equals(second.spoolId()));
        second.close();
    }

    @Test
    void oversizedSingleRecordIsRejected() throws Exception {
        HistorianDiskSpool spool = spool(64L * 1024 * 1024, 64L * 1024 * 1024, true);
        spool.open();
        String huge = "x".repeat(HistorianDiskSpool.MAX_FRAME_BYTES);
        VariableHistoryWriteRecord record = new VariableHistoryWriteRecord(
                "root.a", "v", "value",
                Instant.parse("2026-11-13T00:00:00Z"),
                Instant.parse("2026-11-13T00:00:00Z"),
                null, huge, "GOOD"
        );
        assertThrows(IOException.class, () -> spool.append(List.of(record)));
        spool.close();
    }

    @Test
    void closedChannelWithUntruncatedTailThenSecondAppendKeepsDistinctSeqs() throws Exception {
        HistorianDiskSpool spool = spool(1024 * 1024, 64 * 1024, true);
        spool.open();
        spool.append(List.of(sample(1)));
        long goodEnd = activeSegmentSize(spool);
        closeActiveChannel(spool);
        Files.write(onlySegment(), new byte[] {7, 7, 7, 7}, StandardOpenOption.APPEND);

        String secondId = spool.append(List.of(sample(2)));
        assertEquals(2, Long.parseLong(secondId.substring(secondId.lastIndexOf('-') + 1)));
        assertEquals(Files.size(onlySegment()), activeSegmentSize(spool));
        spool.close();

        HistorianDiskSpool reopened = spool(1024 * 1024, 64 * 1024, true);
        reopened.open();
        List<Double> values = new CopyOnWriteArrayList<>();
        List<Long> seqs = new CopyOnWriteArrayList<>();
        assertEquals(2, reopened.replay((id, records) -> {
            values.add(records.getFirst().valueDouble());
            seqs.add(Long.parseLong(id.substring(id.lastIndexOf('-') + 1)));
        }, 10));
        assertEquals(Set.of(1.0, 2.0), new HashSet<>(values));
        assertEquals(Set.of(1L, 2L), new HashSet<>(seqs));
        assertTrue(activeSegmentSize(reopened) >= goodEnd);
        reopened.close();
    }

    @Test
    void concurrentAppendAndAckReplaysEachUnackedBatchOnce() throws Exception {
        // fsync=false: this test stresses seq assignment / ack races, not durability.
        HistorianDiskSpool spool = spool(8 * 1024 * 1024, 8 * 1024, false);
        spool.open();
        int threads = 8;
        int each = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> ids = new CopyOnWriteArrayList<>();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            int base = thread * each;
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < each; i++) {
                    String id = spool.append(List.of(sample(base + i)));
                    ids.add(id);
                    if ((i & 1) == 0) {
                        spool.ack(id);
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        spool.close();

        HistorianDiskSpool reopened = spool(8 * 1024 * 1024, 8 * 1024, false);
        reopened.open();
        List<String> replayed = new CopyOnWriteArrayList<>();
        int copied;
        do {
            copied = reopened.replay((id, records) -> replayed.add(id), 16);
        } while (copied > 0);
        assertEquals(threads * each, ids.size());
        assertEquals(threads * each, new HashSet<>(ids).size());
        assertEquals(threads * each / 2, replayed.size());
        assertEquals(replayed.size(), new HashSet<>(replayed).size());
        reopened.close();
    }

    private void truncateAfterFrames(int frames) throws Exception {
        Path segment = onlySegment();
        int headerBytes = 20;
        try (FileChannel channel = FileChannel.open(
                segment, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long good = 0;
            ByteBuffer header = ByteBuffer.allocate(headerBytes);
            for (int frame = 0; frame < frames; frame++) {
                header.clear();
                if (!readFully(channel, header)) {
                    break;
                }
                header.flip();
                int magic = header.getInt();
                int length = header.getInt();
                int expected = header.getInt();
                header.getLong();
                if (magic != HistorianDiskSpool.MAGIC || length <= 0) {
                    break;
                }
                ByteBuffer body = ByteBuffer.allocate(length);
                if (!readFully(channel, body)) {
                    break;
                }
                CRC32 crc = new CRC32();
                crc.update(body.array());
                if ((int) crc.getValue() != expected) {
                    break;
                }
                good = channel.position();
            }
            channel.truncate(good);
        }
    }

    private static boolean readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                return false;
            }
        }
        return true;
    }

    private static void closeActiveChannel(HistorianDiskSpool spool) throws Exception {
        Field channelField = HistorianDiskSpool.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        FileChannel channel = (FileChannel) channelField.get(spool);
        channel.close();
    }

    private static long activeSegmentSize(HistorianDiskSpool spool) throws Exception {
        Field sizeField = HistorianDiskSpool.class.getDeclaredField("segmentSize");
        sizeField.setAccessible(true);
        return sizeField.getLong(spool);
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
