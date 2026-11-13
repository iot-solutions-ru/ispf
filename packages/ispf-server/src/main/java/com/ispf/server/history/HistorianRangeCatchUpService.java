package com.ispf.server.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * After a ClickHouse outage, replays primary samples from the disk WAL for every
 * open range in {@code historian_catchup_ranges}.
 */
@Service
public class HistorianRangeCatchUpService {

    private static final Logger log = LoggerFactory.getLogger(HistorianRangeCatchUpService.class);

    /**
     * One ClickHouse insert per open range does not finish a multi-minute outage inside the
     * catch-up wait. The historian flushes faster than sample timestamps overlap, so each flush
     * becomes its own range, and each range is its own HTTP call (30s budget). Replay in chunks
     * and stop on the first failure so a downed ClickHouse does not walk every fragment.
     */
    static final int REPLAY_CHUNK = 2_000;

    private final HistorianSampleWal wal;
    private final HistorianCatchUpRangeStore ranges;
    private final ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary;
    private final Object walLock = new Object();

    @Autowired
    public HistorianRangeCatchUpService(
            HistorianCatchUpRangeStore ranges,
            ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary,
            @Value("${ispf.historian.catchup.dir:}") String catchupDir,
            @Value("${ispf.variable-history.catchup.directory:}") String legacyDir,
            @Value("${ispf.variable-history.catchup.max-bytes:268435456}") long maxBytes
    ) {
        this(new HistorianSampleWal(resolveWalDirectory(catchupDir, legacyDir), maxBytes), ranges, secondary);
    }

    static Path resolveWalDirectory(String catchupDir, String legacyDir) {
        if (catchupDir != null && !catchupDir.isBlank()) {
            return Path.of(catchupDir);
        }
        if (legacyDir != null && !legacyDir.isBlank()) {
            return Path.of(legacyDir);
        }
        String dataDir = System.getenv("ISPF_DATA_DIR");
        if (dataDir == null || dataDir.isBlank()) {
            dataDir = "./data";
        }
        return Path.of(dataDir, "historian-catchup");
    }

    HistorianRangeCatchUpService(
            HistorianSampleWal wal,
            HistorianCatchUpRangeStore ranges,
            ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary
    ) {
        this.wal = wal;
        this.ranges = ranges;
        this.secondary = secondary;
    }

    public void noteSecondaryFailure(List<VariableHistoryWriteRecord> records) {
        List<VariableHistoryWriteRecord> kept = normalize(records);
        if (kept.isEmpty()) {
            return;
        }
        synchronized (walLock) {
            wal.append(kept);
            coverSpans(kept);
        }
    }

    @Scheduled(fixedDelayString = "${ispf.variable-history.catchup.delay-ms:5000}")
    public int replay() {
        try {
            return replayOnce();
        } catch (RuntimeException ex) {
            log.warn("historian catch-up skipped", ex);
            return 0;
        }
    }

    private int replayOnce() {
        ClickHouseVariableHistorySecondaryWriteStore writer = secondary.getIfAvailable();
        if (writer == null) {
            return 0;
        }
        List<HistorianCatchUpRangeStore.Range> open = ranges.list();
        if (open.isEmpty()) {
            return 0;
        }

        List<VariableHistoryWriteRecord> pending;
        synchronized (walLock) {
            pending = dedupe(wal.readAll());
        }
        if (pending.isEmpty()) {
            return 0;
        }
        Set<String> coveredHere = new HashSet<>();
        for (HistorianCatchUpRangeStore.Range range : open) {
            for (VariableHistoryWriteRecord record : pending) {
                if (inRange(record, range)) {
                    coveredHere.add(rangeIdentity(range));
                    break;
                }
            }
        }

        int copied = 0;
        List<VariableHistoryWriteRecord> acknowledged = new ArrayList<>();
        for (int offset = 0; offset < pending.size(); offset += REPLAY_CHUNK) {
            List<VariableHistoryWriteRecord> chunk = new ArrayList<>(
                    pending.subList(offset, Math.min(offset + REPLAY_CHUNK, pending.size()))
            );
            try {
                writer.appendBatch(chunk);
            } catch (RuntimeException ex) {
                log.warn(
                        "historian catch-up replay failed ({} of {} samples, chunk {})",
                        acknowledged.size(),
                        pending.size(),
                        chunk.size(),
                        ex
                );
                break;
            }
            copied += chunk.size();
            acknowledged.addAll(chunk);
        }

        if (acknowledged.isEmpty()) {
            return 0;
        }
        Set<String> acked = new HashSet<>();
        for (VariableHistoryWriteRecord record : acknowledged) {
            acked.add(identity(record));
        }
        synchronized (walLock) {
            List<VariableHistoryWriteRecord> staying = new ArrayList<>();
            for (VariableHistoryWriteRecord record : dedupe(wal.readAll())) {
                if (!acked.contains(identity(record))) {
                    staying.add(record);
                }
            }
            wal.rewrite(staying);
            for (HistorianCatchUpRangeStore.Range range : open) {
                boolean stillOpen = false;
                for (VariableHistoryWriteRecord record : staying) {
                    if (inRange(record, range)) {
                        stillOpen = true;
                        break;
                    }
                }
                if (!stillOpen && coveredHere.contains(rangeIdentity(range))) {
                    ranges.remove(range.seriesKey(), range.from(), range.to());
                }
            }
        }
        return copied;
    }

    private void coverSpans(List<VariableHistoryWriteRecord> records) {
        Map<String, Instant> from = new LinkedHashMap<>();
        Map<String, Instant> to = new LinkedHashMap<>();
        for (VariableHistoryWriteRecord record : records) {
            Instant at = record.sampledAt();
            if (at == null) {
                continue;
            }
            String key = seriesKey(record);
            from.merge(key, at, (left, right) -> left.isBefore(right) ? left : right);
            to.merge(key, at, (left, right) -> left.isAfter(right) ? left : right);
        }
        for (Map.Entry<String, Instant> entry : from.entrySet()) {
            ranges.cover(entry.getKey(), entry.getValue(), to.get(entry.getKey()));
        }
    }

    private static List<VariableHistoryWriteRecord> normalize(List<VariableHistoryWriteRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<VariableHistoryWriteRecord> kept = new ArrayList<>();
        for (VariableHistoryWriteRecord record : records) {
            if (record == null || record.sampledAt() == null) {
                continue;
            }
            kept.add(new VariableHistoryWriteRecord(
                    record.objectPath(),
                    record.variableName(),
                    record.fieldName(),
                    record.sampledAt().truncatedTo(ChronoUnit.MILLIS),
                    record.observedAt() == null ? null : record.observedAt().truncatedTo(ChronoUnit.MILLIS),
                    record.valueDouble(),
                    record.valueText(),
                    record.quality()
            ));
        }
        return dedupe(kept);
    }

    private static List<VariableHistoryWriteRecord> dedupe(List<VariableHistoryWriteRecord> records) {
        Map<String, VariableHistoryWriteRecord> unique = new LinkedHashMap<>();
        for (VariableHistoryWriteRecord record : records) {
            unique.put(identity(record), record);
        }
        return new ArrayList<>(unique.values());
    }

    private static String identity(VariableHistoryWriteRecord record) {
        long at = record.sampledAt() == null ? 0L : record.sampledAt().toEpochMilli();
        return seriesKey(record) + "|" + at + "|" + record.valueDouble() + "|" + record.valueText();
    }

    private static boolean inRange(VariableHistoryWriteRecord record, HistorianCatchUpRangeStore.Range range) {
        Instant at = record.sampledAt();
        return at != null
                && seriesKey(record).equals(range.seriesKey())
                && !at.isBefore(range.from())
                && !at.isAfter(range.to());
    }

    private static String rangeIdentity(HistorianCatchUpRangeStore.Range range) {
        return range.seriesKey() + "|" + range.from().toEpochMilli() + "|" + range.to().toEpochMilli();
    }

    static String seriesKey(VariableHistoryWriteRecord record) {
        return record.objectPath() + "|" + record.variableName() + "|" + record.fieldName();
    }
}
