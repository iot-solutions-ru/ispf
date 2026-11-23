package com.ispf.server.relational;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.config.HistorianColdArchiveProperties;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.config.MetadataDbProperties;
import com.ispf.server.config.MetadataDialectBootPolicy;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.config.MysqlRelationalProperties;
import com.ispf.server.config.OracleRelationalProperties;
import com.ispf.server.config.TenantIsolationProperties;
import com.ispf.server.config.VariableHistoryProperties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ADR-0080: the pre-Flyway customizer is where an Oracle ISPF_DB without the operator driver, or
 * outside B1, must stop — before any location is applied. The default test classpath has no
 * ojdbc; the opt-in Oracle lane (-Dispf.test.oracleDocker=true) adds it.
 */
class FlywayDialectConfigurationOracleTest {

    @Test
    void missingDriverIsRefusedBeforeLocationsAreApplied() {
        assumeFalse(driverPresent(), "Oracle lab driver on the classpath");
        FluentConfiguration configuration = Flyway.configure();
        assertThatThrownBy(() -> customizer(false, false, false).customize(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ISPF_METADATA_DB_KIND=oracle")
                .hasMessageContaining("ADR-0080");
        assertThat(Arrays.stream(configuration.getLocations()).map(Location::getDescriptor))
                .doesNotContain("classpath:db/migration/oracle");
    }

    @Test
    void b1DefaultsSelectOraclePack() {
        assumeTrue(driverPresent(), "Oracle lab driver not on the classpath");
        FluentConfiguration configuration = Flyway.configure();
        customizer(false, false, false).customize(configuration);
        assertThat(Arrays.stream(configuration.getLocations()).map(Location::getDescriptor))
                .containsExactly("classpath:db/migration/oracle");
    }

    @Test
    void b1ClusterIsRefusedBeforeMigrate() {
        assumeTrue(driverPresent(), "Oracle lab driver not on the classpath");
        assertThatThrownBy(() -> customizer(true, false, false).customize(Flyway.configure()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ispf.cluster.enabled=true on Oracle requires ISPF_ORACLE_SINGLE_ENGINE=true")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void jdbcHistoryIsRefusedBeforeMigrate() {
        assumeTrue(driverPresent(), "Oracle lab driver not on the classpath");
        assertThatThrownBy(() -> customizer(false, true, false).customize(Flyway.configure()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variable-history")
                .hasMessageContaining("B2");
    }

    @Test
    void singleEngineClusterSelectsOraclePack() {
        assumeTrue(driverPresent(), "Oracle lab driver not on the classpath");
        FluentConfiguration configuration = Flyway.configure();
        customizer(true, true, true).customize(configuration);
        assertThat(Arrays.stream(configuration.getLocations()).map(Location::getDescriptor))
                .containsExactly("classpath:db/migration/oracle");
    }

    private static boolean driverPresent() {
        return ClassUtils.isPresent(
                MetadataDialectBootPolicy.ORACLE_JDBC_DRIVER,
                FlywayDialectConfigurationOracleTest.class.getClassLoader()
        );
    }

    private static FlywayConfigurationCustomizer customizer(
            boolean clusterEnabled,
            boolean jdbcHistory,
            boolean singleEngine
    ) {
        MetadataDbProperties metadata = new MetadataDbProperties();
        metadata.setKind("oracle");
        RelationalDialectDetector detector = new RelationalDialectDetector(
                Map.of(RelationalDbKind.ORACLE, new OracleDialect()), metadata);
        TenantIsolationProperties tenant = new TenantIsolationProperties();
        tenant.setDbRowIsolation(false);
        tenant.setIsolationMode(TenantIsolationProperties.IsolationMode.LOGICAL);
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setEnabled(jdbcHistory);
        history.setStore(jdbcHistory ? "jdbc" : "clickhouse");
        history.setDualWriteEnabled(false);
        EventJournalProperties journal = new EventJournalProperties();
        journal.setEnabled(singleEngine);
        journal.setStore("jdbc");
        ClusterProperties cluster = mock(ClusterProperties.class);
        when(cluster.enabled()).thenReturn(clusterEnabled);
        OracleRelationalProperties oracle = new OracleRelationalProperties();
        oracle.setSingleEngine(singleEngine);
        return new FlywayDialectConfiguration().flywayDialectCustomizer(
                null,
                detector,
                tenant,
                history,
                journal,
                cluster,
                new MssqlRelationalProperties(),
                new MysqlRelationalProperties(),
                oracle,
                new HistorianTierProperties(),
                new HistorianColdArchiveProperties(false, "", 200, 10000)
        );
    }
}
