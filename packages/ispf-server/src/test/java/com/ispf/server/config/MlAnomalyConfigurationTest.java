package com.ispf.server.config;

import com.ispf.core.ml.AnomalyDetectionSpi;
import com.ispf.server.ml.NoOpAnomalyDetectionSpi;
import com.ispf.server.ml.ThresholdAnomalyDetectionSpi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-06: slice the ML SPI wiring with {@link ApplicationContextRunner} instead of full server boot.
 */
class MlAnomalyConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void wiresNoOpSpiByDefault() {
        runner.run(context -> {
            AnomalyDetectionSpi spi = context.getBean(AnomalyDetectionSpi.class);
            assertThat(spi).isInstanceOf(NoOpAnomalyDetectionSpi.class);
            assertThat(spi.modelId()).isEqualTo(NoOpAnomalyDetectionSpi.MODEL_ID);
            assertThat(spi.score("root.test", "temperature", java.util.List.of())).isEmpty();
            assertThat(context.getBean(MlAnomalyProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void wiresThresholdSpiWhenEnabled() {
        runner.withPropertyValues("ispf.ml.anomaly.enabled=true").run(context -> {
            assertThat(context.getBean(AnomalyDetectionSpi.class)).isInstanceOf(ThresholdAnomalyDetectionSpi.class);
            assertThat(context.getBean(MlAnomalyProperties.class).isEnabled()).isTrue();
        });
    }

    @Configuration
    @Import(MlAnomalyConfiguration.class)
    @EnableConfigurationProperties(MlAnomalyProperties.class)
    static class TestConfig {
    }
}
