package com.ispf.server.history;

import com.ispf.server.platform.AutomationMetricsRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * BL-116: PostgreSQL/Timescale primary, then ClickHouse secondary. A secondary failure is an error of that history write.
 */
@Service
@Primary
@ConditionalOnProperty(name = "ispf.variable-history.dual-write-enabled", havingValue = "true")
@ConditionalOnProperty(name = "ispf.historian.tiers.warm.enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(name = "ispf.variable-history.store", havingValue = "jdbc", matchIfMissing = true)
public class DualWriteVariableHistoryWriteStore implements VariableHistoryWriteStore {

    private final JdbcVariableHistoryWriteStore primary;
    private final ClickHouseVariableHistorySecondaryWriteStore secondary;
    private final AutomationMetricsRecorder metrics;
    private final HistorianAheadSet primaryAheadOfSecondary;

    @Autowired
    public DualWriteVariableHistoryWriteStore(
            JdbcVariableHistoryWriteStore primary,
            ClickHouseVariableHistorySecondaryWriteStore secondary,
            AutomationMetricsRecorder metrics
    ) {
        this(primary, secondary, metrics, HistorianAheadSet.DEFAULT_MAX_KEYS);
    }

    DualWriteVariableHistoryWriteStore(
            JdbcVariableHistoryWriteStore primary,
            ClickHouseVariableHistorySecondaryWriteStore secondary,
            AutomationMetricsRecorder metrics,
            int maxAheadKeys
    ) {
        this.primary = primary;
        this.secondary = secondary;
        this.metrics = metrics;
        this.primaryAheadOfSecondary = new HistorianAheadSet(maxAheadKeys);
    }

    @Override
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
        writePrimary(records, false);
        appendSecondary(records);
    }

    @Override
    public void appendOne(VariableHistoryWriteRecord record) {
        writePrimary(List.of(record), true);
        appendSecondary(List.of(record));
    }

    private void writePrimary(List<VariableHistoryWriteRecord> records, boolean single) {
        if (records.isEmpty()) {
            return;
        }
        List<VariableHistoryWriteRecord> missingOnPrimary = new ArrayList<>();
        for (VariableHistoryWriteRecord record : records) {
            if (!primaryAheadOfSecondary.contains(primaryKey(record))) {
                missingOnPrimary.add(record);
            }
        }
        if (missingOnPrimary.isEmpty()) {
            return;
        }
        if (single && missingOnPrimary.size() == 1) {
            primary.appendOne(missingOnPrimary.getFirst());
        } else {
            primary.appendBatch(missingOnPrimary);
        }
        for (VariableHistoryWriteRecord record : missingOnPrimary) {
            primaryAheadOfSecondary.add(primaryKey(record));
        }
    }

    int aheadSize() {
        return primaryAheadOfSecondary.size();
    }

    private void appendSecondary(List<VariableHistoryWriteRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        try {
            secondary.appendBatch(records);
        } catch (RuntimeException ex) {
            metrics.recordHistorianDualWriteSecondaryFailure();
            throw new IllegalStateException(
                    "variable history secondary write failed; secondary store is behind primary",
                    ex
            );
        }
        for (VariableHistoryWriteRecord record : records) {
            primaryAheadOfSecondary.remove(primaryKey(record));
        }
    }

    private static String primaryKey(VariableHistoryWriteRecord record) {
        return record.objectPath()
                + "\n" + record.variableName()
                + "\n" + record.fieldName()
                + "\n" + record.sampledAt()
                + "\n" + record.valueDouble()
                + "\n" + record.valueText();
    }
}
