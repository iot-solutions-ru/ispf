package com.ispf.server.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * BL-116: PostgreSQL/Timescale primary + best-effort ClickHouse secondary append.
 */
@Service
@Primary
@ConditionalOnProperty(name = "ispf.variable-history.dual-write-enabled", havingValue = "true")
@ConditionalOnProperty(name = "ispf.variable-history.store", havingValue = "jdbc", matchIfMissing = true)
public class DualWriteVariableHistoryWriteStore implements VariableHistoryWriteStore {

    private static final Logger log = LoggerFactory.getLogger(DualWriteVariableHistoryWriteStore.class);

    private final JdbcVariableHistoryWriteStore primary;
    private final ClickHouseVariableHistorySecondaryWriteStore secondary;

    public DualWriteVariableHistoryWriteStore(
            JdbcVariableHistoryWriteStore primary,
            ClickHouseVariableHistorySecondaryWriteStore secondary
    ) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
        primary.appendBatch(records);
        appendSecondary(records);
    }

    @Override
    public void appendOne(VariableHistoryWriteRecord record) {
        primary.appendOne(record);
        appendSecondary(List.of(record));
    }

    private void appendSecondary(List<VariableHistoryWriteRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        try {
            secondary.appendBatch(records);
        } catch (RuntimeException ex) {
            log.warn("ClickHouse dual-write append failed ({} records): {}", records.size(), ex.getMessage());
        }
    }
}
