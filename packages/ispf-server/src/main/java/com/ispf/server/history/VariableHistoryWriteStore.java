package com.ispf.server.history;

import java.util.List;

/**
 * High-throughput append path for {@code variable_samples}.
 * Reads use {@link VariableHistoryQueryStore}; retention purge is store-specific.
 */
public interface VariableHistoryWriteStore {

    void appendBatch(List<VariableHistoryWriteRecord> records);

    void appendOne(VariableHistoryWriteRecord record);
}
