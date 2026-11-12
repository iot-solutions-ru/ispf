package com.ispf.server.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        if (records == null || records.isEmpty()) {
            return;
        }
        wal.append(records);
        for (VariableHistoryWriteRecord record : records) {
            if (record.sampledAt() == null) {
                continue;
            }
            ranges.cover(seriesKey(record), record.sampledAt(), record.sampledAt());
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
        List<VariableHistoryWriteRecord> stillPending = new ArrayList<>(wal.readAll());
        int copied = 0;
        for (HistorianCatchUpRangeStore.Range range : open) {
            List<VariableHistoryWriteRecord> batch = new ArrayList<>();
            for (VariableHistoryWriteRecord record : stillPending) {
                if (!seriesKey(record).equals(range.seriesKey())) {
                    continue;
                }
                Instant at = record.sampledAt();
                if (at != null && !at.isBefore(range.from()) && !at.isAfter(range.to())) {
                    batch.add(record);
                }
            }
            if (batch.isEmpty()) {
                ranges.remove(range.seriesKey(), range.from(), range.to());
                continue;
            }
            try {
                writer.appendBatch(batch);
            } catch (RuntimeException ex) {
                log.warn("historian catch-up replay failed for {} ({} samples)", range.seriesKey(), batch.size(), ex);
                continue;
            }
            stillPending.removeAll(batch);
            ranges.remove(range.seriesKey(), range.from(), range.to());
            copied += batch.size();
        }
        wal.rewrite(stillPending);
        return copied;
    }

    static String seriesKey(VariableHistoryWriteRecord record) {
        return record.objectPath() + "|" + record.variableName() + "|" + record.fieldName();
    }
}
