package com.ispf.server.relational;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.config.HistorianColdArchiveProperties;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.config.MetadataDbProperties;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.config.TenantIsolationProperties;
import com.ispf.server.config.VariableHistoryProperties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ADR-0079: with the hard engine refuse lifted, the pre-Flyway customizer is where a MySQL
 * ISPF_DB outside B1 must still stop — before any migration runs.
 */
class FlywayDialectConfigurationMysqlTest {

    @Test
    void b1DefaultsSelectMysqlPack() {
        FluentConfiguration configuration = Flyway.configure();
        customizer(false, false).customize(configuration);
        assertThat(Arrays.stream(configuration.getLocations()).map(Location::getDescriptor))
                .containsExactly("classpath:db/migration/mysql");
    }

    @Test
    void clusterIsRefusedBeforeMigrate() {
        assertThatThrownBy(() -> customizer(true, false).customize(Flyway.configure()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cluster")
                .hasMessageContaining("ADR-0079");
    }

    @Test
    void jdbcHistoryIsRefusedBeforeMigrate() {
        assertThatThrownBy(() -> customizer(false, true).customize(Flyway.configure()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variable-history")
                .hasMessageContaining("B2");
    }

    private static FlywayConfigurationCustomizer customizer(boolean clusterEnabled, boolean jdbcHistory) {
        MetadataDbProperties metadata = new MetadataDbProperties();
        metadata.setKind("mysql");
        RelationalDialectDetector detector = new RelationalDialectDetector(
                Map.of(RelationalDbKind.MYSQL, new MysqlDialect()), metadata);
        TenantIsolationProperties tenant = new TenantIsolationProperties();
        tenant.setDbRowIsolation(false);
        tenant.setIsolationMode(TenantIsolationProperties.IsolationMode.LOGICAL);
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setEnabled(jdbcHistory);
        history.setStore("jdbc");
        history.setDualWriteEnabled(false);
        EventJournalProperties journal = new EventJournalProperties();
        journal.setEnabled(false);
        ClusterProperties cluster = mock(ClusterProperties.class);
        when(cluster.enabled()).thenReturn(clusterEnabled);
        return new FlywayDialectConfiguration().flywayDialectCustomizer(
                null,
                detector,
                tenant,
                history,
                journal,
                cluster,
                new MssqlRelationalProperties(),
                new HistorianTierProperties(),
                new HistorianColdArchiveProperties(false, "", 200, 10000)
        );
    }
}
