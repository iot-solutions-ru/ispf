package com.ispf.server.config;

import com.ispf.server.relational.MysqlDialect;
import com.ispf.server.relational.OracleDialect;
import com.ispf.server.relational.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MysqlCertificationBootGuardTest {

    @Test
    void refusesRowIsolationOnMysql() {
        MysqlCertificationBootGuard guard = newGuard(
                true, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db-row-isolation");
    }

    @Test
    void refusesHardTenantModeOnMysql() {
        MysqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.HARD, false, "clickhouse", false, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HARD");
    }

    @Test
    void refusesDualWriteOnMysql() {
        MysqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "clickhouse", true, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dual-write");
    }

    @Test
    void refusesClusterOnMysql() {
        MysqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", true);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cluster");
    }

    @Test
    void refusesJdbcHistoryOnMysql() {
        MysqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, true, "jdbc", false, false, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variable-history");
    }

    @Test
    void refusesJdbcJournalOnMysql() {
        MysqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, true, "jdbc", false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event-journal");
    }

    @Test
    void allowsB1MysqlDefaults() {
        MysqlCertificationBootGuard guard = newGuard(
                false, TenantIsolationProperties.IsolationMode.LOGICAL, false, "clickhouse", false, false, "jdbc", false);
        assertThatCode(guard::refuseUnsupportedMysqlModes).doesNotThrowAnyException();
    }

    @Test
    void ignoresPostgres() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(true);
        VariableHistoryProperties history = new VariableHistoryProperties();
        EventJournalProperties journal = new EventJournalProperties();
        ClusterProperties cluster = mock(ClusterProperties.class);
        when(cluster.enabled()).thenReturn(true);
        assertThatCode(() -> new MysqlCertificationBootGuard(
                new PostgreSqlDialect(), props, history, journal, cluster
        ).refuseUnsupportedMysqlModes()).doesNotThrowAnyException();
    }

    @Test
    void engineGatePassesMysqlSoFencesDecide() {
        assertThatCode(() -> MetadataDialectBootPolicy.assertEngineCertified(new MysqlDialect()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> MetadataDialectBootPolicy.assertEngineCertified(
                new OracleDialect(), cls -> false, res -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-0080");
    }

    private static MysqlCertificationBootGuard newGuard(
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
        return new MysqlCertificationBootGuard(
                new MysqlDialect(), props, history, journal, cluster
        );
    }
}
