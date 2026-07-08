package com.ispf.server.ml;

import com.ispf.core.ml.AnomalyDetectionResult;
import com.ispf.core.ml.AnomalyDetectionSpi;
import com.ispf.server.config.MlAnomalyProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reference threshold anomaly model (BL-175) — flags samples outside configured bounds.
 */
public class ThresholdAnomalyDetectionSpi implements AnomalyDetectionSpi {

    public static final String MODEL_ID = "threshold-v1";

    private final MlAnomalyProperties properties;

    public ThresholdAnomalyDetectionSpi(MlAnomalyProperties properties) {
        this.properties = properties;
    }

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
        if (samples == null || samples.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> latest = samples.get(samples.size() - 1);
        Double value = toDouble(latest.get("value"));
        if (value == null) {
            return Optional.empty();
        }

        double min = properties.getThresholdMin();
        double max = properties.getThresholdMax();
        boolean belowMin = value < min;
        boolean aboveMax = value > max;
        if (!belowMin && !aboveMax) {
            return Optional.empty();
        }

        double distance = belowMin ? (min - value) : (value - max);
        double span = Math.max(max - min, 1.0);
        double score = Math.min(1.0, distance / span);
        boolean anomaly = score >= properties.getDefaultThreshold();
        String label = belowMin ? "below_min" : "above_max";

        return Optional.of(new AnomalyDetectionResult(
                MODEL_ID,
                score,
                anomaly,
                label,
                Instant.now(),
                Map.of(
                        "value", value,
                        "min", min,
                        "max", max,
                        "objectPath", objectPath,
                        "variableName", variableName
                )
        ));
    }

    private static Double toDouble(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
