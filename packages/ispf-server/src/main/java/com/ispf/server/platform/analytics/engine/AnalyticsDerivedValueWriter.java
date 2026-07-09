package com.ispf.server.platform.analytics.engine;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Persists analytics derived outputs with calculation timestamp for cluster sync (BL-204).
 */
@Service
public class AnalyticsDerivedValueWriter {

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;

    public AnalyticsDerivedValueWriter(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public void write(String path, String variable, String value, Instant observedAt) {
        Instant effectiveObservedAt = observedAt != null ? observedAt : Instant.now();
        objectManager.setVariableValue(
                path,
                variable,
                DataRecord.single(STRING_VALUE, Map.of("value", value != null ? value : "")),
                effectiveObservedAt
        );
    }
}
