package com.ispf.server.config;

import com.ispf.core.ml.AnomalyDetectionSpi;
import com.ispf.server.ml.NoOpAnomalyDetectionSpi;
import com.ispf.server.ml.ThresholdAnomalyDetectionSpi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ML anomaly SPI wiring (BL-175).
 */
@Configuration
public class MlAnomalyConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ispf.ml.anomaly", name = "enabled", havingValue = "true")
    AnomalyDetectionSpi thresholdAnomalyDetectionSpi(MlAnomalyProperties properties) {
        return new ThresholdAnomalyDetectionSpi(properties);
    }

    @Bean
    @ConditionalOnMissingBean(AnomalyDetectionSpi.class)
    AnomalyDetectionSpi noOpAnomalyDetectionSpi() {
        return new NoOpAnomalyDetectionSpi();
    }
}
