package com.ispf.server.config;

import com.ispf.server.relational.MssqlDialect;
import com.ispf.server.relational.MysqlDialect;
import com.ispf.server.relational.OracleDialect;
import com.ispf.server.relational.PostgreSqlDialect;
import com.ispf.server.relational.RelationalDialect;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OracleCertificationBootGuardTest {

    @Test
    void refusesRowIsolationOnOracle() {
        OracleCertificationBootGuard guard = newGuard(
                true, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", false, false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db-row-isolation")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void refusesHardTenantModeOnOracle() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.HARD, false, "clickhouse", false, false, "jdbc", false, false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HARD")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void refusesDualWriteOnOracle() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "clickhouse", true, false, "jdbc", false, false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dual-write")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void refusesClusterOnOracleWithoutSingleEngine() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", true, false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ispf.cluster.enabled=true on Oracle requires ISPF_ORACLE_SINGLE_ENGINE=true")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void singleEngineAllowsCluster() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jdbc", false, true, "jdbc", true, true);
        assertThatCode(guard::refuseUnsupportedOracleModes).doesNotThrowAnyException();
    }

    @Test
    void refusesJdbcHistoryOnOracleWithoutSingleEngineBypass() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jdbc", false, false, "jdbc", false, false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variable-history")
                .hasMessageContaining("B2")
                .hasMessageContaining("SINGLE_ENGINE");
    }

    @Test
    void refusesJdbcJournalOnOracleWithoutSingleEngineBypass() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, true, "jdbc", false, false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event-journal")
                .hasMessageContaining("B2")
                .hasMessageContaining("SINGLE_ENGINE");
    }

    @Test
    void singleEngineAllowsJdbcHistoryAndJournal() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jdbc", false, true, "jdbc", false, true);
        assertThatCode(guard::refuseUnsupportedOracleModes).doesNotThrowAnyException();
    }

    @Test
    void singleEngineRefusesWarmTier() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(false);
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setEnabled(true);
        history.setStore("jdbc");
        EventJournalProperties journal = new EventJournalProperties();
        journal.setEnabled(true);
        journal.setStore("jdbc");
        ClusterProperties cluster = mock(ClusterProperties.class);
        when(cluster.enabled()).thenReturn(false);
        OracleRelationalProperties oracle = new OracleRelationalProperties();
        oracle.setSingleEngine(true);
        HistorianTierProperties tiers = new HistorianTierProperties();
        tiers.warmTier().setEnabled(true);
        HistorianColdArchiveProperties cold = new HistorianColdArchiveProperties(false, "", 200, 10000);
        OracleCertificationBootGuard guard = new OracleCertificationBootGuard(
                new OracleDialect(), props, history, journal, cluster, oracle, tiers, cold);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("warm-tier");
    }

    @Test
    void singleEngineFlagOnOtherEngineRefused() {
        assertThatThrownBy(() -> MetadataDialectBootPolicy.assertOracleB1Fences(
                new PostgreSqlDialect(),
                new TenantIsolationProperties(),
                new VariableHistoryProperties(),
                new EventJournalProperties(),
                mock(ClusterProperties.class),
                true,
                false,
                false
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ISPF_ORACLE_SINGLE_ENGINE")
                .hasMessageContaining("POSTGRESQL");
    }

    @Test
    void allowsB1OracleDefaults() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", false, false);
        assertThatCode(guard::refuseUnsupportedOracleModes).doesNotThrowAnyException();
        OracleCertificationBootGuard externalStores = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "clickhouse", false, true, "clickhouse", false, false);
        assertThatCode(externalStores::refuseUnsupportedOracleModes).doesNotThrowAnyException();
    }

    @Test
    void ignoresOtherEngines() {
        for (RelationalDialect dialect : new RelationalDialect[]{
                new PostgreSqlDialect(), new MssqlDialect(), new MysqlDialect()}) {
            TenantIsolationProperties props = new TenantIsolationProperties();
            props.setDbRowIsolation(true);
            VariableHistoryProperties history = new VariableHistoryProperties();
            EventJournalProperties journal = new EventJournalProperties();
            ClusterProperties cluster = mock(ClusterProperties.class);
            when(cluster.enabled()).thenReturn(true);
            assertThatCode(() -> newGuardForDialect(
                    dialect, props, history, journal, cluster, false
            ).refuseUnsupportedOracleModes()).doesNotThrowAnyException();
        }
    }

    @Test
    void engineGateNeedsTheFlywayPackAndTheOperatorDriver() {
        OracleDialect oracle = new OracleDialect();
        assertThatThrownBy(() -> MetadataDialectBootPolicy.assertEngineCertified(oracle, cls -> true, res -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Flyway pack")
                .hasMessageContaining("ADR-0080");
        assertThatThrownBy(() -> MetadataDialectBootPolicy.assertEngineCertified(oracle, cls -> false, res -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MetadataDialectBootPolicy.ORACLE_JDBC_DRIVER)
                .hasMessageContaining("does not ship")
                .hasMessageContaining("-Dloader.path=lib")
                .hasMessageContaining("ADR-0080");
        assertThatCode(() -> MetadataDialectBootPolicy.assertEngineCertified(oracle, cls -> true, res -> true))
                .doesNotThrowAnyException();
    }

    @Test
    void engineGateIgnoresOtherEngines() {
        for (RelationalDialect dialect : new RelationalDialect[]{
                new PostgreSqlDialect(), new MssqlDialect(), new MysqlDialect()}) {
            assertThatCode(() -> MetadataDialectBootPolicy.assertEngineCertified(dialect, cls -> false, res -> false))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void bootGuardFollowsTheRuntimeClasspath() {
        assertThat(new ClassPathResource(MetadataDialectBootPolicy.ORACLE_B1_BASELINE).exists()).isTrue();
        UncertifiedRelationalEngineBootGuard guard = new UncertifiedRelationalEngineBootGuard(new OracleDialect());
        if (ClassUtils.isPresent(MetadataDialectBootPolicy.ORACLE_JDBC_DRIVER, getClass().getClassLoader())) {
            assertThatCode(guard::refuseUncertifiedEngines).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(guard::refuseUncertifiedEngines)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ojdbc17.jar");
        }
    }

    private static OracleCertificationBootGuard newGuard(
            boolean rowIsolation,
            TenantIsolationProperties.IsolationMode isolationMode,
            boolean historyEnabled,
            String historyStore,
            boolean dualWrite,
            boolean journalEnabled,
            String journalStore,
            boolean clusterEnabled,
            boolean singleEngine
    ) {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(rowIsolation);
        props.setIsolationMode(isolationMode);
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setEnabled(historyEnabled);
        history.setStore(historyStore);
        history.setDualWriteEnabled(dualWrite);
        EventJournalProperties journal = new EventJournalProperties();
        journal.setEnabled(journalEnabled);
        journal.setStore(journalStore);
        ClusterProperties cluster = mock(ClusterProperties.class);
        when(cluster.enabled()).thenReturn(clusterEnabled);
        return newGuardForDialect(new OracleDialect(), props, history, journal, cluster, singleEngine);
    }

    private static OracleCertificationBootGuard newGuardForDialect(
            RelationalDialect dialect,
            TenantIsolationProperties props,
            VariableHistoryProperties history,
            EventJournalProperties journal,
            ClusterProperties cluster,
            boolean singleEngine
    ) {
        OracleRelationalProperties oracle = new OracleRelationalProperties();
        oracle.setSingleEngine(singleEngine);
        HistorianTierProperties tiers = new HistorianTierProperties();
        HistorianColdArchiveProperties cold = new HistorianColdArchiveProperties(false, "", 200, 10000);
        return new OracleCertificationBootGuard(
                dialect, props, history, journal, cluster, oracle, tiers, cold);
    }
}
