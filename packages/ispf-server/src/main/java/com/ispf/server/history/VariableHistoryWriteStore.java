package com.ispf.server.history;

import java.util.List;

/**
 * High-throughput append path for {@code variable_samples}.
 * Reads and retention purge remain on JPA / {@link com.ispf.server.persistence.VariableSampleRepository}.
 */
public interface VariableHistoryWriteStore {

    void appendBatch(List<VariableHistoryWriteRecord> records);

    void appendOne(VariableHistoryWriteRecord record);
}
