package com.ispf.server.ml;

import com.ispf.core.ml.AnomalyDetectionResult;
import com.ispf.core.ml.AnomalyDetectionSpi;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default no-op anomaly model (BL-175) — always returns empty until a real SPI is on the classpath.
 */
public class NoOpAnomalyDetectionSpi implements AnomalyDetectionSpi {

    public static final String MODEL_ID = "noop";

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    @Override
    public Optional<AnomalyDetectionResult> score(
            String objectPath,
            String variableName,
            List<Map<String, Object>> samples
    ) {
        return Optional.empty();
    }
}
