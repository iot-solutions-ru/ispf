package com.ispf.server.history;

import com.ispf.server.persistence.entity.VariableSampleEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VariableHistoryBatchPersister {

    private final VariableHistoryWriteStore writeStore;

    public VariableHistoryBatchPersister(VariableHistoryWriteStore writeStore) {
        this.writeStore = writeStore;
    }

    public void persistBatch(List<VariableSampleEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        writeStore.appendBatch(batch.stream().map(VariableHistoryWriteRecord::fromEntity).toList());
    }

    public void persistOne(VariableSampleEntity sample) {
        writeStore.appendOne(VariableHistoryWriteRecord.fromEntity(sample));
    }
}
