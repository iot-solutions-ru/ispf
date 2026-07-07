package com.ispf.core.ml;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service provider interface for variable-history anomaly models (BL-175).
 * Implementations are discovered at runtime when {@code ispf.ml.anomaly.enabled=true}.
 */
public interface AnomalyDetectionSpi {

    String modelId();

    /**
     * Score a single variable sample window.
     *
     * @param objectPath  source object path
     * @param variableName variable being scored
     * @param samples chronological samples (newest last); each map has at least {@code value} and {@code timestamp}
     */
    Optional<AnomalyDetectionResult> score(
            String objectPath,
            String variableName,
            List<Map<String, Object>> samples
    );
}
