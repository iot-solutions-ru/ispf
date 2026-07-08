package com.ispf.server.config;

import com.ispf.core.ml.AnomalyDetectionSpi;
import com.ispf.server.ml.NoOpAnomalyDetectionSpi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MlAnomalyConfigurationTest {

    @Autowired
    private AnomalyDetectionSpi anomalyDetectionSpi;

    @Autowired
    private MlAnomalyProperties mlAnomalyProperties;

    @Test
    void wiresNoOpSpiByDefault() {
        assertThat(anomalyDetectionSpi).isInstanceOf(NoOpAnomalyDetectionSpi.class);
        assertThat(anomalyDetectionSpi.modelId()).isEqualTo(NoOpAnomalyDetectionSpi.MODEL_ID);
        assertThat(anomalyDetectionSpi.score("root.test", "temperature", java.util.List.of())).isEmpty();
        assertThat(mlAnomalyProperties.isEnabled()).isFalse();
    }
}
