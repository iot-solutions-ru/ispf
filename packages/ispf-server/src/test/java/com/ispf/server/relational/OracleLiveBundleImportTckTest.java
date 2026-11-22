package com.ispf.server.relational;

import com.ispf.server.audit.AuditChainStore;
import com.ispf.server.persistence.AlarmShelfRepository;
import com.ispf.server.persistence.entity.AlarmShelfEntity;
import jakarta.persistence.EntityManagerFactory;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0080 B1: live Spring Boot boot + package import against Oracle Database Free Testcontainers.
 * Skips unless Docker and the lab ojdbc driver are both available ({@link EnabledIf}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "admin")
@Tag("oracle-docker")
@EnabledIf("com.ispf.server.relational.OracleLiveBundleImportTckTest#available")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
class OracleLiveBundleImportTckTest {

    @SuppressWarnings("resource")
    static final OracleContainer ORACLE = available() ? OracleTestSupport.newContainer() : null;

    static {
        if (ORACLE != null) {
            ORACLE.start();
        }
    }

    @AfterAll
    static void stopContainer() {
        if (ORACLE != null && ORACLE.isRunning()) {
            ORACLE.stop();
        }
    }

    @DynamicPropertySource
    static void oracleProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> OracleTestSupport.DRIVER_CLASS);
        // The test profile pool (200) is sized for H2 load tests, not an Oracle Free session budget.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("ispf.metadata.db.kind", () -> "oracle");
        registry.add("ispf.tenant.db-row-isolation", () -> "false");
        registry.add("ispf.bootstrap.fixtures-enabled", () -> "false");
        // ADR-0080 B1: jdbc history / journal / cluster are B2 or uncertified on Oracle.
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
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AuditChainStore auditChainStore;

    @Autowired
    AlarmShelfRepository alarmShelfRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void importsOracleB1CertBundleAndResolvesKeyPaths() throws Exception {
        assertThat(entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getJdbcServices().getDialect()).isInstanceOf(org.hibernate.dialect.OracleDialect.class);
        assertThat(dataSource).isInstanceOf(OracleUuidJdbcDataSource.class);

        String bundle = new ClassPathResource("oracle-b1-cert-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        for (int attempt = 0; attempt < 2; attempt++) {
            // The second import takes the update path of every store the first one inserted into.
            mockMvc.perform(post("/api/v1/platform/packages/import")
                            .param("packageId", "oracle-b1-cert")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bundle))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.applicationPath").value("root.platform.applications.oracle-b1-cert"));
        }

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.devices.oracle-b1-cert-sensor-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.devices.oracle-b1-cert-sensor-01"));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.platform.dashboards.oracle-b1-cert-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.dashboards.oracle-b1-cert-overview"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_bundle_deployments WHERE app_id = 'oracle-b1-cert'",
                Integer.class
        )).isEqualTo(1);
        assertThat(auditChainStore.verify()).isTrue();
    }

    @Test
    void alarmShelfRoundTripsThroughJpa() {
        // COMMENT is an Oracle reserved word; JPQL also renders boolean literals and Instant binds here.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        AlarmShelfEntity shelf = new AlarmShelfEntity();
        shelf.setId("oracle-tck-shelf");
        shelf.setObjectPath("root.platform.devices.oracle-b1-cert-sensor-01");
        shelf.setEventName("overheat");
        shelf.setShelvedBy("tck");
        shelf.setShelvedAt(now);
        shelf.setExpiresAt(now.plusSeconds(3600));
        shelf.setComment("maintenance window");
        shelf.setActive(true);
        alarmShelfRepository.saveAndFlush(shelf);

        AlarmShelfEntity found = alarmShelfRepository
                .findActiveShelf("root.platform.devices.oracle-b1-cert-sensor-01", "overheat", now)
                .orElseThrow();
        assertThat(found.getComment()).isEqualTo("maintenance window");
        assertThat(found.getShelvedAt()).isEqualTo(now);
        assertThat(alarmShelfRepository.findByActiveTrueOrderByShelvedAtDesc()).extracting(AlarmShelfEntity::getId)
                .contains("oracle-tck-shelf");
        Integer deactivated = transactionTemplate.execute(
                status -> alarmShelfRepository.deactivateExpired(now.plusSeconds(7200)));
        assertThat(deactivated).isEqualTo(1);
    }

    static boolean available() {
        return OracleTestSupport.available();
    }
}
