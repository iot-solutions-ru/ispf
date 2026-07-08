package com.ispf.server.ml;

import com.ispf.core.ml.AnomalyDetectionResult;
import com.ispf.core.ml.AnomalyDetectionSpi;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.MlAnomalyProperties;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates alert rules backed by {@link AnomalyDetectionSpi} (BL-175).
 */
@Service
public class AnomalyAlertRuleEvaluator {

    private final AnomalyDetectionSpi anomalyDetectionSpi;
    private final MlAnomalyProperties properties;
    private final VariableHistoryService variableHistoryService;
    private final ObjectManager objectManager;

    public AnomalyAlertRuleEvaluator(
            AnomalyDetectionSpi anomalyDetectionSpi,
            MlAnomalyProperties properties,
            VariableHistoryService variableHistoryService,
            ObjectManager objectManager
    ) {
        this.anomalyDetectionSpi = anomalyDetectionSpi;
        this.properties = properties;
        this.variableHistoryService = variableHistoryService;
        this.objectManager = objectManager;
    }

    public boolean evaluate(String objectPath, String watchVariable, String anomalyModelId) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (anomalyModelId == null || anomalyModelId.isBlank()) {
            return false;
        }
        if (!anomalyModelId.equals(anomalyDetectionSpi.modelId())) {
            return false;
        }

        List<Map<String, Object>> samples = buildSamples(objectPath, watchVariable);
        if (samples.isEmpty()) {
            return false;
        }

        Optional<AnomalyDetectionResult> result = anomalyDetectionSpi.score(objectPath, watchVariable, samples);
        return result.map(AnomalyDetectionResult::anomaly).orElse(false);
    }

    private List<Map<String, Object>> buildSamples(String objectPath, String watchVariable) {
        List<Map<String, Object>> samples = new ArrayList<>();
        Instant to = Instant.now();
        Instant from = to.minus(Math.max(1, properties.getSampleWindowSeconds()), ChronoUnit.SECONDS);

        try {
            VariableHistoryService.VariableHistoryResponse response = variableHistoryService.query(
                    objectPath,
                    watchVariable,
                    "value",
                    from,
                    to,
                    100
            );
            for (VariableHistoryService.VariableHistorySample sample : response.samples()) {
                if (sample.value() == null) {
                    continue;
                }
                samples.add(Map.of(
                        "value", sample.value(),
                        "timestamp", sample.ts().toString()
                ));
            }
        } catch (RuntimeException ignored) {
            // Fall back to the live variable value when history is unavailable.
        }

        Double current = readCurrentValue(objectPath, watchVariable);
        if (current != null) {
            boolean alreadyLatest = !samples.isEmpty()
                    && current.equals(((Number) samples.get(samples.size() - 1).get("value")).doubleValue());
            if (!alreadyLatest) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("value", current);
                point.put("timestamp", to.toString());
                samples.add(point);
            }
        }
        return samples;
    }

    private Double readCurrentValue(String objectPath, String watchVariable) {
        PlatformObject node = objectManager.require(objectPath);
        return node.getVariable(watchVariable)
                .flatMap(v -> v.value())
                .map(record -> record.firstRow().get("value"))
                .map(value -> {
                    if (value instanceof Number number) {
                        return number.doubleValue();
                    }
                    if (value instanceof Boolean bool) {
                        return bool ? 1.0 : 0.0;
                    }
                    return null;
                })
                .orElse(null);
    }
}
