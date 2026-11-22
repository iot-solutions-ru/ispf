package com.ispf.server.relational;

import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0080 B1: always-on certification bundle fixture gate.
 * Live Spring Boot import: {@link OracleLiveBundleImportTckTest}.
 */
class OracleBundleImportTckTest {

    @Test
    void certBundleIsTreeOnlyAndParses() throws Exception {
        String json = new ClassPathResource("oracle-b1-cert-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        ApplicationBundleDeployService.BundleManifest manifest = new ObjectMapper()
                .readValue(json, ApplicationBundleDeployService.BundleManifest.class);

        assertThat(manifest.version()).isEqualTo("1.0.0");
        assertThat(manifest.displayName()).contains("Oracle");
        assertThat(manifest.schemaName()).isEqualTo("app_oracle_b1_cert");
        assertThat(manifest.migrations() == null || manifest.migrations().isEmpty()).isTrue();
        assertThat(manifest.objects()).isNotEmpty();
        assertThat(manifest.blueprints()).isNotEmpty();
        assertThat(manifest.dashboards()).isNotEmpty();
        assertThat(manifest.objects().getFirst().type()).isEqualTo("DEVICE");
        assertThat(manifest.blueprints().getFirst().name()).isEqualTo("oracle-b1-cert-hub-v1");
        assertThat(manifest.blueprints().getFirst().type()).isNotNull();
        assertThat(manifest.blueprints().getFirst().type().name()).isEqualTo("SINGLETON");
    }
}
