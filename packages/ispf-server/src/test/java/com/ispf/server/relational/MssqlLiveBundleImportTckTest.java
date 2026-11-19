package com.ispf.server.relational;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0078 Phase 3: live Spring Boot package import against MSSQL Testcontainers.
 * Skips when Docker is unavailable ({@link EnabledIf}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "admin")
@Tag("mssql-docker")
@EnabledIf("com.ispf.server.relational.MssqlLiveBundleImportTckTest#dockerAvailable")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class MssqlLiveBundleImportTckTest {

    @SuppressWarnings("resource")
    static final MSSQLServerContainer MSSQL = new MSSQLServerContainer(
            "mcr.microsoft.com/mssql/server:2022-latest"
    )
            .acceptLicense()
            .withStartupTimeout(Duration.ofMinutes(5));

    static String jdbcUrl;

    static {
        if (dockerAvailable()) {
            MSSQL.start();
            jdbcUrl = MssqlTestSupport.prepareIspfDatabase(MSSQL);
        }
    }

    @AfterAll
    static void stopContainer() {
        if (MSSQL.isRunning()) {
            MSSQL.stop();
        }
    }

    @DynamicPropertySource
    static void mssqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", MSSQL::getUsername);
        registry.add("spring.datasource.password", MSSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MSSQL::getDriverClassName);
        registry.add("ispf.metadata.db.kind", () -> "mssql");
        registry.add("ispf.tenant.db-row-isolation", () -> "false");
        registry.add("ispf.bootstrap.fixtures-enabled", () -> "false");
        // ADR-0078 B1: jdbc history / journal / cluster are B2 or uncertified on MSSQL.
        registry.add("ispf.variable-history.enabled", () -> "false");
        registry.add("ispf.variable-history.dual-write-enabled", () -> "false");
        registry.add("ispf.event-journal.enabled", () -> "false");
        registry.add("ispf.cluster.enabled", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void importsMssqlB1CertBundleAndResolvesKeyPaths() throws Exception {
        String bundle = new ClassPathResource("mssql-b1-cert-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .param("packageId", "mssql-b1-cert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.applicationPath").value("root.platform.applications.mssql-b1-cert"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.devices.mssql-b1-cert-sensor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.devices.mssql-b1-cert-sensor-01"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.dashboards.mssql-b1-cert-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.dashboards.mssql-b1-cert-overview"));
    }

    static boolean dockerAvailable() {
        return MssqlTestSupport.dockerAvailable();
    }
}
