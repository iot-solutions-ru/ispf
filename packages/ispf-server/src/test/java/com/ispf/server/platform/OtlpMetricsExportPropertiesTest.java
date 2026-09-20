package com.ispf.server.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-06: property smoke without {@code @SpringBootTest} (was a full-context boot for one YAML key).
 */
class OtlpMetricsExportPropertiesTest {

    @Test
    void otlpMetricsExportDisabledByDefaultInTestProfile() throws IOException {
        assertThat(testProfileProperty("management.otlp.metrics.export.enabled")).isEqualTo(false);
    }

    static Object testProfileProperty(String key) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-test", new ClassPathResource("application-test.yml"));
        assertThat(sources).isNotEmpty();
        return sources.getFirst().getProperty(key);
    }
}
