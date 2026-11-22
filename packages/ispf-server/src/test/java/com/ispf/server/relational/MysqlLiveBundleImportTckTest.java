package com.ispf.server.relational;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0079 B1: live Spring Boot boot with Hibernate {@code ddl-auto=validate} + package import against
 * MySQL 8.4 Testcontainers. Skips when Docker is unavailable ({@link EnabledIf}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "admin")
@Tag("mysql-docker")
@EnabledIf("com.ispf.server.relational.MysqlLiveBundleImportTckTest#dockerAvailable")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class MysqlLiveBundleImportTckTest {

    @SuppressWarnings("resource")
    static final MySQLContainer MYSQL = MysqlTestSupport.newContainer();

    static {
        if (dockerAvailable()) {
            MYSQL.start();
        }
    }

    @AfterAll
    static void stopContainer() {
        if (MYSQL.isRunning()) {
            MYSQL.stop();
        }
    }

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> MysqlTestSupport.DRIVER_CLASS);
        // The test profile pool (200) exceeds MySQL's default max_connections (151).
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("ispf.metadata.db.kind", () -> "mysql");
        // Production default (application.yml); the test profile sets none.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("ispf.tenant.db-row-isolation", () -> "false");
        registry.add("ispf.bootstrap.fixtures-enabled", () -> "false");
        // ADR-0079 B1: jdbc history / journal / cluster are B2 or uncertified on MySQL.
        registry.add("ispf.variable-history.enabled", () -> "false");
        registry.add("ispf.variable-history.dual-write-enabled", () -> "false");
        registry.add("ispf.event-journal.enabled", () -> "false");
        registry.add("ispf.cluster.enabled", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void validatesEntitiesAgainstCaseSensitiveServer() {
        // Validation runs while the context starts; a missing table or column fails the boot.
        assertThat(entityManagerFactory.getProperties()).containsEntry(SchemaToolingSettings.HBM2DDL_AUTO, "validate");
        // 0 is the Linux default and the cell where Connector/J reports no identifier case.
        assertThat(jdbcTemplate.queryForObject("SELECT @@lower_case_table_names", Integer.class)).isZero();
    }

    @Test
    void importsMysqlB1CertBundleAndResolvesKeyPaths() throws Exception {
        // MariaDB Connector/J would make Hibernate pick MariaDBDialect (a MySQLDialect subclass).
        assertThat(entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getJdbcServices().getDialect().getClass())
                .isEqualTo(MysqlHibernateDialect.class);

        String bundle = new ClassPathResource("mysql-b1-cert-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .param("packageId", "mysql-b1-cert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.applicationPath").value("root.platform.applications.mysql-b1-cert"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.devices.mysql-b1-cert-sensor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.devices.mysql-b1-cert-sensor-01"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.dashboards.mysql-b1-cert-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.dashboards.mysql-b1-cert-overview"));
    }

    static boolean dockerAvailable() {
        return MysqlTestSupport.dockerAvailable();
    }
}
