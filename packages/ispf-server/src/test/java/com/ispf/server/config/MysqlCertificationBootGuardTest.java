package com.ispf.server.config;

import com.ispf.server.relational.H2Dialect;
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
                .hasMessageContaining("variable-history")
                .hasMessageContaining("ISPF_MYSQL_SINGLE_ENGINE=true");
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
    void singleEngineAllowsJdbcHistoryAndJournal() {
        MysqlCertificationBootGuard guard = singleEngineGuard(true, "jdbc", false, true, "jdbc", false, false, false);
        assertThatCode(guard::refuseUnsupportedMysqlModes).doesNotThrowAnyException();
    }

    @Test
    void singleEngineAllowsJdbcHistoryWithJournalOff() {
        MysqlCertificationBootGuard guard = singleEngineGuard(true, "jdbc", false, false, "jdbc", false, false, false);
        assertThatCode(guard::refuseUnsupportedMysqlModes).doesNotThrowAnyException();
    }

    @Test
    void singleEngineStillRefusesCluster() {
        MysqlCertificationBootGuard guard = singleEngineGuard(true, "jdbc", false, true, "jdbc", true, false, false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ispf.cluster.enabled=true is not certified on MySQL")
                .hasMessageContaining("single-engine");
    }

    @Test
    void singleEngineStillRefusesDualWrite() {
        MysqlCertificationBootGuard guard = singleEngineGuard(true, "jdbc", true, true, "jdbc", false, false, false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dual-write");
    }

    @Test
    void singleEngineStillRefusesRowIsolationAndHardMode() {
        TenantIsolationProperties rls = new TenantIsolationProperties();
        rls.setDbRowIsolation(true);
        assertThatThrownBy(() -> guard(new MysqlDialect(), rls, history(true, "jdbc", false),
                journal(true, "jdbc"), false, true, false, false).refuseUnsupportedMysqlModes())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db-row-isolation");

        TenantIsolationProperties hard = new TenantIsolationProperties();
        hard.setDbRowIsolation(false);
        hard.setIsolationMode(TenantIsolationProperties.IsolationMode.HARD);
        assertThatThrownBy(() -> guard(new MysqlDialect(), hard, history(true, "jdbc", false),
                journal(true, "jdbc"), false, true, false, false).refuseUnsupportedMysqlModes())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HARD");
    }

    @Test
    void singleEngineRefusesWarmTier() {
        MysqlCertificationBootGuard guard = singleEngineGuard(true, "jdbc", false, true, "jdbc", false, true, false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MySQL single-engine refuses ClickHouse warm-tier")
                .hasMessageContaining("mysql-single-engine");
    }

    @Test
    void singleEngineRefusesColdArchive() {
        MysqlCertificationBootGuard guard = singleEngineGuard(true, "jdbc", false, true, "jdbc", false, false, true);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cold Parquet archive");
    }

    @Test
    void singleEngineRefusesClickHouseSpool() {
        VariableHistoryProperties history = history(true, "jdbc", false);
        history.getSpool().setEnabled(true);
        MysqlCertificationBootGuard guard = guard(
                new MysqlDialect(), b1Tenant(), history, journal(true, "jdbc"), false, true, false, false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ISPF_VARIABLE_HISTORY_SPOOL_ENABLED")
                .hasMessageContaining("On MySQL set it false");
    }

    @Test
    void singleEngineRefusesNonJdbcHistoryStores() {
        for (String store : new String[]{"clickhouse", "cassandra", "jpa", "jbdc"}) {
            MysqlCertificationBootGuard guard = singleEngineGuard(true, store, false, true, "jdbc", false, false, false);
            assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MySQL single-engine requires ISPF_VARIABLE_HISTORY_STORE=jdbc")
                    .hasMessageContaining("'" + store + "'");
        }
    }

    @Test
    void singleEngineRefusesExternalJournalStore() {
        MysqlCertificationBootGuard guard = singleEngineGuard(true, "jdbc", false, true, "clickhouse", false, false, false);
        assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MySQL single-engine requires ISPF_EVENT_JOURNAL_STORE=jdbc");
    }

    @Test
    void singleEngineFlagIsRefusedOnOtherEngines() {
        for (RelationalDialect dialect : new RelationalDialect[]{
                new PostgreSqlDialect(), new H2Dialect(), new MssqlDialect(), new OracleDialect()}) {
            MysqlCertificationBootGuard guard = guard(
                    dialect, b1Tenant(), history(true, "jdbc", false), journal(true, "jdbc"), false, true, false, false);
            assertThatThrownBy(guard::refuseUnsupportedMysqlModes)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ISPF_MYSQL_SINGLE_ENGINE=true applies to a MySQL ISPF_DB only")
                    .hasMessageContaining(dialect.kind().name());
        }
    }

    @Test
    void ignoresPostgres() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(true);
        assertThatCode(() -> guard(new PostgreSqlDialect(), props, new VariableHistoryProperties(),
                new EventJournalProperties(), true, false, false, false).refuseUnsupportedMysqlModes())
                .doesNotThrowAnyException();
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
        return guard(
                new MysqlDialect(),
                props,
                history(historyEnabled, historyStore, dualWrite),
                journal(journalEnabled, journalStore),
                clusterEnabled,
                false,
                false,
                false
        );
    }

    private static MysqlCertificationBootGuard singleEngineGuard(
            boolean historyEnabled,
            String historyStore,
            boolean dualWrite,
            boolean journalEnabled,
            String journalStore,
            boolean clusterEnabled,
            boolean warmEnabled,
            boolean coldArchive
    ) {
        return guard(
                new MysqlDialect(),
                b1Tenant(),
                history(historyEnabled, historyStore, dualWrite),
                journal(journalEnabled, journalStore),
                clusterEnabled,
                true,
                warmEnabled,
                coldArchive
        );
    }

    private static MysqlCertificationBootGuard guard(
            RelationalDialect dialect,
            TenantIsolationProperties tenant,
            VariableHistoryProperties history,
            EventJournalProperties journal,
            boolean clusterEnabled,
            boolean singleEngine,
            boolean warmEnabled,
            boolean coldArchive
    ) {
        ClusterProperties cluster = mock(ClusterProperties.class);
        when(cluster.enabled()).thenReturn(clusterEnabled);
        MysqlRelationalProperties mysql = new MysqlRelationalProperties();
        mysql.setSingleEngine(singleEngine);
        HistorianTierProperties tiers = new HistorianTierProperties();
        tiers.warmTier().setEnabled(warmEnabled);
        HistorianColdArchiveProperties cold = new HistorianColdArchiveProperties(coldArchive, "", 200, 10000);
        return new MysqlCertificationBootGuard(dialect, tenant, history, journal, cluster, mysql, tiers, cold);
    }

    private static TenantIsolationProperties b1Tenant() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(false);
        props.setIsolationMode(TenantIsolationProperties.IsolationMode.LOGICAL);
        return props;
    }

    private static VariableHistoryProperties history(boolean enabled, String store, boolean dualWrite) {
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setEnabled(enabled);
        history.setStore(store);
        history.setDualWriteEnabled(dualWrite);
        return history;
    }

    private static EventJournalProperties journal(boolean enabled, String store) {
        EventJournalProperties journal = new EventJournalProperties();
        journal.setEnabled(enabled);
        journal.setStore(store);
        return journal;
    }
}
