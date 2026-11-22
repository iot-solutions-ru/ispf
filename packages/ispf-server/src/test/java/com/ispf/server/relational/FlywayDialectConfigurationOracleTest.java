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
 * ADR-0080 Phase 1: the pre-Flyway customizer refuses Oracle before any location is applied,
 * so no Oracle migration can run even with B1-clean settings.
 */
class FlywayDialectConfigurationOracleTest {

    @Test
    void b1CleanOracleIsRefusedBeforeLocationsAreApplied() {
        FluentConfiguration configuration = Flyway.configure();
        assertThatThrownBy(() -> customizer(false).customize(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ISPF_METADATA_DB_KIND=oracle")
                .hasMessageContaining("ADR-0080");
        assertThat(Arrays.stream(configuration.getLocations()).map(Location::getDescriptor))
                .doesNotContain("classpath:db/migration/oracle");
    }

    @Test
    void engineRefuseWinsOverB1Fences() {
        assertThatThrownBy(() -> customizer(true).customize(Flyway.configure()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ISPF_METADATA_DB_KIND=oracle")
                .hasMessageNotContaining("ispf.cluster.enabled");
    }

    private static FlywayConfigurationCustomizer customizer(boolean clusterEnabled) {
        MetadataDbProperties metadata = new MetadataDbProperties();
        metadata.setKind("oracle");
        RelationalDialectDetector detector = new RelationalDialectDetector(
                Map.of(RelationalDbKind.ORACLE, new OracleDialect()), metadata);
        TenantIsolationProperties tenant = new TenantIsolationProperties();
        tenant.setDbRowIsolation(false);
        tenant.setIsolationMode(TenantIsolationProperties.IsolationMode.LOGICAL);
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setStore("clickhouse");
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
