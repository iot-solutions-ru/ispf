package com.ispf.server.history;

import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.persistence.entity.VariableSampleEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Legacy JPA write path ({@code saveAll}); use {@code ispf.variable-history.store=jdbc} for throughput. */
@Service
@ConditionalOnProperty(name = "ispf.variable-history.store", havingValue = "jpa")
public class JpaVariableHistoryWriteStore implements VariableHistoryWriteStore {

    private final VariableSampleRepository sampleRepository;

    public JpaVariableHistoryWriteStore(VariableSampleRepository sampleRepository) {
        this.sampleRepository = sampleRepository;
    }

    @Override
    @Transactional
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        List<VariableSampleEntity> entities = records.stream().map(JpaVariableHistoryWriteStore::toEntity).toList();
        sampleRepository.saveAll(entities);
    }

    @Override
    @Transactional
    public void appendOne(VariableHistoryWriteRecord record) {
        sampleRepository.save(toEntity(record));
    }

    private static VariableSampleEntity toEntity(VariableHistoryWriteRecord record) {
        VariableSampleEntity entity = new VariableSampleEntity();
        entity.setObjectPath(record.objectPath());
        entity.setVariableName(record.variableName());
        entity.setFieldName(record.fieldName());
        entity.setSampledAt(record.sampledAt());
        entity.setObservedAt(record.observedAt());
        entity.setValueDouble(record.valueDouble());
        entity.setValueText(record.valueText());
        return entity;
    }
}
