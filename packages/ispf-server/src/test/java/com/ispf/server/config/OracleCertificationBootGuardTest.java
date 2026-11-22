package com.ispf.server.config;

import com.ispf.server.relational.MssqlDialect;
import com.ispf.server.relational.MysqlDialect;
import com.ispf.server.relational.OracleDialect;
import com.ispf.server.relational.PostgreSqlDialect;
import com.ispf.server.relational.RelationalDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OracleCertificationBootGuardTest {

    @Test
    void refusesRowIsolationOnOracle() {
        OracleCertificationBootGuard guard = newGuard(
                true, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db-row-isolation")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void refusesHardTenantModeOnOracle() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.HARD, false, "clickhouse", false, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HARD")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void refusesDualWriteOnOracle() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "clickhouse", true, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dual-write")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void refusesClusterOnOracle() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", true);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cluster")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void refusesJdbcHistoryOnOracleWithoutSingleEngineBypass() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jdbc", false, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variable-history")
                .hasMessageContaining("B2")
                .hasMessageNotContaining("SINGLE_ENGINE");
    }

    @Test
    void refusesJdbcJournalOnOracleWithoutSingleEngineBypass() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, true, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedOracleModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event-journal")
                .hasMessageContaining("B2")
                .hasMessageNotContaining("SINGLE_ENGINE");
    }

    @Test
    void mssqlSingleEngineFlagDoesNotReachOracle() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(false);
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setEnabled(true);
        history.setStore("jdbc");
        EventJournalProperties journal = new EventJournalProperties();
        ClusterProperties cluster = mock(ClusterProperties.class);
        OracleDialect oracle = new OracleDialect();
        assertThatCode(() -> MetadataDialectBootPolicy.assertMssqlB1Fences(
                oracle, props, history, journal, cluster, true, false, false
        )).doesNotThrowAnyException();
        assertThatThrownBy(() -> MetadataDialectBootPolicy.assertOracleB1Fences(
                oracle, props, history, journal, cluster
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variable-history");
    }

    @Test
    void allowsB1OracleDefaults() {
        OracleCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", false);
        assertThatCode(guard::refuseUnsupportedOracleModes).doesNotThrowAnyException();
        OracleCertificationBootGuard externalStores = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "clickhouse", false, true, "clickhouse", false);
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
            assertThatCode(() -> new OracleCertificationBootGuard(
                    dialect, props, history, journal, cluster
            ).refuseUnsupportedOracleModes()).doesNotThrowAnyException();
        }
    }

    @Test
    void engineRefuseStillBlocksOracle() {
        assertThatThrownBy(() -> MetadataDialectBootPolicy.assertEngineCertified(new OracleDialect()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oracle")
                .hasMessageContaining("ADR-0080");
        assertThatThrownBy(() -> new UncertifiedRelationalEngineBootGuard(new OracleDialect())
                .refuseUncertifiedEngines())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-0080");
    }

    private static OracleCertificationBootGuard newGuard(
            boolean rowIsolation,
            TenantIsolationProperties.IsolationMode isolationMode,
            boolean historyEnabled,
            String historyStore,
            boolean dualWrite,
            boolean journalEnabled,
            String journalStore,
            boolean clusterEnabled
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
        return new OracleCertificationBootGuard(
                new OracleDialect(), props, history, journal, cluster
        );
    }
}
