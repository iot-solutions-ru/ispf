package com.ispf.server.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-06: property smoke without {@code @SpringBootTest} (was a full-context boot for one YAML key).
 */
class OtlpTracingExportPropertiesTest {

    @Test
    void tracingExportDisabledByDefaultInTestProfile() throws Exception {
        assertThat(OtlpMetricsExportPropertiesTest.testProfileProperty("management.tracing.enabled"))
                .isEqualTo(false);
    }
}
