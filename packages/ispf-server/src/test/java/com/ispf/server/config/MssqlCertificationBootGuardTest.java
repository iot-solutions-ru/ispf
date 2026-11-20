package com.ispf.server.config;

import com.ispf.server.relational.H2Dialect;
import com.ispf.server.relational.MssqlDialect;
import com.ispf.server.relational.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MssqlCertificationBootGuardTest {

    @Test
    void refusesMssqlWhenRowIsolationOn() {
        MssqlCertificationBootGuard guard = newGuard(
                true, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db-row-isolation");
    }

    @Test
    void refusesHardTenantModeOnMssql() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.HARD, false, "clickhouse", false, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HARD");
    }

    @Test
    void refusesNonExternalHistoryStoreTypo() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "clikhouse", false, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variable-history");
    }

    @Test
    void refusesDualWriteOnMssql() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "clickhouse", true, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dual-write");
    }

    @Test
    void refusesJdbcEventJournalWhenEnabled() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, true, "jpa", false);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event-journal");
    }

    @Test
    void refusesClusterOnMssql() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", true);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cluster");
    }

    @Test
    void allowsB1MssqlDefaults() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", false);
        assertThatCode(guard::refuseUnsupportedMssqlModes).doesNotThrowAnyException();
    }

    @Test
    void ignoresPostgresAndH2() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(true);
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setStore("jdbc");
        EventJournalProperties journal = new EventJournalProperties();
        ClusterProperties cluster = mock(ClusterProperties.class);
        when(cluster.enabled()).thenReturn(true);
        MssqlRelationalProperties mssql = new MssqlRelationalProperties();
        HistorianTierProperties tiers = new HistorianTierProperties();
        HistorianColdArchiveProperties cold = new HistorianColdArchiveProperties(false, "", 200, 10000);
        assertThatCode(() -> new MssqlCertificationBootGuard(
                new PostgreSqlDialect(), props, history, journal, cluster, mssql, tiers, cold
        ).refuseUnsupportedMssqlModes()).doesNotThrowAnyException();
        assertThatCode(() -> new MssqlCertificationBootGuard(
                new H2Dialect(), props, history, journal, cluster, mssql, tiers, cold
        ).refuseUnsupportedMssqlModes()).doesNotThrowAnyException();
    }

    @Test
    void singleEngineAllowsJdbcHistoryAndJournal() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jdbc", false, true, "jdbc", false,
                true, false, false);
        assertThatCode(guard::refuseUnsupportedMssqlModes).doesNotThrowAnyException();
    }

    @Test
    void singleEngineRefusesWarmTier() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jdbc", false, true, "jdbc", false,
                true, true, false);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("warm-tier");
    }

    @Test
    void singleEngineRefusesJpaHistoryStore() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jpa", false, true, "jdbc", false,
                true, false, false);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jpa");
    }

    @Test
    void singleEngineRefusesHistoryStoreTypo() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jbdc", false, true, "jdbc", false,
                true, false, false);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jbdc");
    }

    @Test
    void b1StillRefusesJdbcHistory() {
        MssqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jdbc", false, false, "jdbc", false,
                false, false, false);
        assertThatThrownBy(guard::refuseUnsupportedMssqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variable-history");
    }

    private static MssqlCertificationBootGuard newGuard(
            boolean rowIsolation,
            TenantIsolationProperties.IsolationMode isolationMode,
            boolean historyEnabled,
            String historyStore,
            boolean dualWrite,
            boolean journalEnabled,
            String journalStore,
            boolean clusterEnabled
    ) {
        return newGuard(
                rowIsolation, isolationMode, historyEnabled, historyStore, dualWrite,
                journalEnabled, journalStore, clusterEnabled, false, false, false);
    }

    private static MssqlCertificationBootGuard newGuard(
            boolean rowIsolation,
            TenantIsolationProperties.IsolationMode isolationMode,
            boolean historyEnabled,
            String historyStore,
            boolean dualWrite,
            boolean journalEnabled,
            String journalStore,
            boolean clusterEnabled,
            boolean singleEngine,
            boolean warmEnabled,
            boolean coldArchive
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
        MssqlRelationalProperties mssql = new MssqlRelationalProperties();
        mssql.setSingleEngine(singleEngine);
        HistorianTierProperties tiers = new HistorianTierProperties();
        tiers.warmTier().setEnabled(warmEnabled);
        HistorianColdArchiveProperties cold = new HistorianColdArchiveProperties(coldArchive, "", 200, 10000);
        return new MssqlCertificationBootGuard(
                new MssqlDialect(),
                props,
                history,
                journal,
                cluster,
                mssql,
                tiers,
                cold
        );
    }
}
