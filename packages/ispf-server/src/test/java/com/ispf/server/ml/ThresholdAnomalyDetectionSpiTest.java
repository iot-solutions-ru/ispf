package com.ispf.server.ml;

import com.ispf.core.ml.AnomalyDetectionResult;
import com.ispf.server.config.MlAnomalyProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ThresholdAnomalyDetectionSpiTest {

    @Test
    void flagsValueAboveMaxThreshold() {
        MlAnomalyProperties properties = new MlAnomalyProperties();
        properties.setThresholdMin(0);
        properties.setThresholdMax(100);
        properties.setDefaultThreshold(0.5);

        ThresholdAnomalyDetectionSpi spi = new ThresholdAnomalyDetectionSpi(properties);
        Optional<AnomalyDetectionResult> result = spi.score(
                "root.test.device",
                "temperature",
                List.of(Map.of("value", 150, "timestamp", "2026-01-01T00:00:00Z"))
        );

        assertThat(result).isPresent();
        assertThat(result.get().anomaly()).isTrue();
        assertThat(result.get().label()).isEqualTo("above_max");
        assertThat(result.get().modelId()).isEqualTo(ThresholdAnomalyDetectionSpi.MODEL_ID);
    }

    @Test
    void returnsEmptyWhenValueWithinBounds() {
        MlAnomalyProperties properties = new MlAnomalyProperties();
        properties.setThresholdMin(10);
        properties.setThresholdMax(90);

        ThresholdAnomalyDetectionSpi spi = new ThresholdAnomalyDetectionSpi(properties);
        Optional<AnomalyDetectionResult> result = spi.score(
                "root.test.device",
                "temperature",
                List.of(Map.of("value", 42))
        );

        assertThat(result).isEmpty();
    }
}
