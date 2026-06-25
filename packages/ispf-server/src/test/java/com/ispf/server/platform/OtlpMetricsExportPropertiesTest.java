package com.ispf.server.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OtlpMetricsExportPropertiesTest {

    @Autowired
    private Environment environment;

    @Test
    void otlpMetricsExportDisabledByDefaultInTestProfile() {
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled", Boolean.class, false))
                .isFalse();
    }
}
