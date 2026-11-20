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
        assertThatCode(() -> new MssqlCertificationBootGuard(
                new PostgreSqlDialect(), props, history, journal, cluster
        ).refuseUnsupportedMssqlModes()).doesNotThrowAnyException();
        assertThatCode(() -> new MssqlCertificationBootGuard(
                new H2Dialect(), props, history, journal, cluster
        ).refuseUnsupportedMssqlModes()).doesNotThrowAnyException();
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
        return new MssqlCertificationBootGuard(
                new MssqlDialect(),
                props,
                history,
                journal,
                cluster
        );
    }
}
