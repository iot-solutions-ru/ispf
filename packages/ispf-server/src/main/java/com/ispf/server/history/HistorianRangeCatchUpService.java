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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * After a ClickHouse outage, replays primary samples from the disk WAL for every
 * open range in {@code historian_catchup_ranges}.
 */
@Service
public class HistorianRangeCatchUpService {

    private static final Logger log = LoggerFactory.getLogger(HistorianRangeCatchUpService.class);

    private final HistorianSampleWal wal;
    private final HistorianCatchUpRangeStore ranges;
    private final ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary;
    private final Object walLock = new Object();

    @Autowired
    public HistorianRangeCatchUpService(
            HistorianCatchUpRangeStore ranges,
            ObjectProvider<ClickHouseVariableHistorySecondaryWriteStore> secondary,
            @Value("${ispf.variable-history.catchup.directory:${java.io.tmpdir}/ispf-historian-catchup}") String directory,
            @Value("${ispf.variable-history.catchup.max-bytes:268435456}") long maxBytes
    ) {
        this(new HistorianSampleWal(Path.of(directory), maxBytes), ranges, secondary);
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
        List<HistorianCatchUpRangeStore.Range> open;
        List<VariableHistoryWriteRecord> claimed;
        synchronized (walLock) {
            open = ranges.list();
            if (open.isEmpty()) {
                return 0;
            }
            List<VariableHistoryWriteRecord> staying = new ArrayList<>();
            claimed = new ArrayList<>();
            for (VariableHistoryWriteRecord record : dedupe(wal.readAll())) {
                if (coveredBy(record, open)) {
                    claimed.add(record);
                } else {
                    staying.add(record);
                }
            }
            wal.rewrite(staying);
            for (HistorianCatchUpRangeStore.Range range : open) {
                ranges.remove(range.seriesKey(), range.from(), range.to());
            }
        }
        int copied = 0;
        for (HistorianCatchUpRangeStore.Range range : open) {
            List<VariableHistoryWriteRecord> batch = new ArrayList<>();
            for (VariableHistoryWriteRecord record : claimed) {
                if (inRange(record, range)) {
                    batch.add(record);
                }
            }
            if (batch.isEmpty()) {
                continue;
            }
            try {
                writer.appendBatch(batch);
                copied += batch.size();
            } catch (RuntimeException ex) {
                log.warn("historian catch-up replay failed for {} ({} samples)", range.seriesKey(), batch.size(), ex);
                synchronized (walLock) {
                    wal.append(batch);
                    coverSpans(batch);
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

    private static boolean coveredBy(
            VariableHistoryWriteRecord record,
            List<HistorianCatchUpRangeStore.Range> open
    ) {
        for (HistorianCatchUpRangeStore.Range range : open) {
            if (inRange(record, range)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inRange(VariableHistoryWriteRecord record, HistorianCatchUpRangeStore.Range range) {
        Instant at = record.sampledAt();
        return at != null
                && seriesKey(record).equals(range.seriesKey())
                && !at.isBefore(range.from())
                && !at.isAfter(range.to());
    }

    static String seriesKey(VariableHistoryWriteRecord record) {
        return record.objectPath() + "|" + record.variableName() + "|" + record.fieldName();
    }
}
