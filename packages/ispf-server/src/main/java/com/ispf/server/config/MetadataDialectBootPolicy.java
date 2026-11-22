package com.ispf.server.config;

import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;

/**
 * Shared ADR-0078 / 0079 / 0037 boot fences. Invoked before Flyway locations are applied and again
 * on {@code ContextRefreshedEvent} so disallowed engines never migrate.
 */
public final class MetadataDialectBootPolicy {

    private MetadataDialectBootPolicy() {
    }

    /**
     * Hard engine refuse. MySQL is not refused here: like MSSQL it boots only under its B1 fences
     * ({@link #assertMysqlB1Fences}), which every caller of this method also runs.
     */
    public static void assertEngineCertified(RelationalDialect dialect) {
        RelationalDbKind kind = dialect.kind();
        if (kind == RelationalDbKind.ORACLE) {
            throw new IllegalStateException(
                    "ISPF_METADATA_DB_KIND=oracle is not certified (ADR-0037 tier 4 / ADR-0080). "
                            + "Oracle starts only after MySQL B1. "
                            + "Use postgresql, h2, or mssql under ADR-0078. "
                            + "External DATA_SOURCE JDBC to Oracle for reports remains supported."
            );
        }
    }

    /**
     * MySQL B1 fences (ADR-0079 Lab green). No single-engine bypass — JDBC history/journal on
     * ISPF_DB is a later B2 decision. These fences are the only thing between a MySQL ISPF_DB and
     * an uncertified mode; keep them before Flyway and on context refresh.
     */
    public static void assertMysqlB1Fences(
            RelationalDialect dialect,
            TenantIsolationProperties tenantIsolationProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            ClusterProperties clusterProperties
    ) {
        if (dialect.kind() != RelationalDbKind.MYSQL) {
            return;
        }
        if (tenantIsolationProperties.isDbRowIsolation()) {
            throw new IllegalStateException(
                    "ispf.tenant.db-row-isolation=true is unsupported on MySQL (ADR-0079). "
                            + "Set ISPF_TENANT_DB_ROW_ISOLATION=false for single-tenant lab, "
                            + "or use PostgreSQL until a native session security policy exists."
            );
        }
        if (tenantIsolationProperties.isHardMode()) {
            throw new IllegalStateException(
                    "ispf.tenant.isolation-mode=HARD is unsupported on MySQL (ADR-0079). "
                            + "Per-tenant schemas require PostgreSQL search_path."
            );
        }
        if (variableHistoryProperties.isDualWriteEnabled()) {
            throw new IllegalStateException(
                    "ispf.variable-history.dual-write-enabled=true is refused on MySQL (ADR-0079). "
                            + "B1 keeps history off ISPF_DB JDBC."
            );
        }
        if (clusterProperties.enabled()) {
            throw new IllegalStateException(
                    "ispf.cluster.enabled=true is not certified on MySQL (ADR-0079). "
                            + "Run a single JVM, or use PostgreSQL for active-active."
            );
        }
        if (variableHistoryProperties.isEnabled() && !variableHistoryProperties.isExternalTimeSeriesStore()) {
            throw new IllegalStateException(
                    "ispf.variable-history store falls back to ISPF_DB JDBC on MySQL (B2, ADR-0079). "
                            + "Set ISPF_VARIABLE_HISTORY_STORE=clickhouse (or cassandra/scylla), "
                            + "or ISPF_VARIABLE_HISTORY_ENABLED=false for B1."
            );
        }
        if (eventJournalProperties.isEnabled() && eventJournalProperties.isJdbcStore()) {
            throw new IllegalStateException(
                    "ispf.event-journal store falls back to ISPF_DB JDBC on MySQL (B2, ADR-0079). "
                            + "Set ISPF_EVENT_JOURNAL_STORE=clickhouse (or cassandra/scylla), "
                            + "or ISPF_EVENT_JOURNAL_ENABLED=false for B1."
            );
        }
    }

    public static void assertMssqlB1Fences(
            RelationalDialect dialect,
            TenantIsolationProperties tenantIsolationProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            ClusterProperties clusterProperties,
            boolean singleEngine,
            boolean warmEnabled,
            boolean coldArchiveEnabled
    ) {
        if (dialect.kind() != RelationalDbKind.MSSQL) {
            return;
        }
        if (tenantIsolationProperties.isDbRowIsolation()) {
            throw new IllegalStateException(
                    "ispf.tenant.db-row-isolation=true is unsupported on MSSQL (ADR-0078). "
                            + "Set ISPF_TENANT_DB_ROW_ISOLATION=false for single-tenant deployments, "
                            + "or use PostgreSQL until a native SESSION_CONTEXT security policy exists."
            );
        }
        if (tenantIsolationProperties.isHardMode()) {
            throw new IllegalStateException(
                    "ispf.tenant.isolation-mode=HARD is unsupported on MSSQL (ADR-0078 §4). "
                            + "Per-tenant schemas require PostgreSQL search_path."
            );
        }
        if (variableHistoryProperties.isDualWriteEnabled()) {
            throw new IllegalStateException(
                    "ispf.variable-history.dual-write-enabled=true is refused on MSSQL (ADR-0078). "
                            + "Single-engine mode keeps one JDBC store. Disable dual-write."
            );
        }
        if (clusterProperties.enabled()) {
            throw new IllegalStateException(
                    "ispf.cluster.enabled=true is not certified on MSSQL (ADR-0078 Phase 6). "
                            + "Run a single JVM, or use PostgreSQL for active-active until an MSSQL soak passes."
            );
        }
        if (singleEngine) {
            assertSingleEngineStores(variableHistoryProperties, eventJournalProperties, warmEnabled, coldArchiveEnabled);
            return;
        }
        if (variableHistoryProperties.isEnabled() && !variableHistoryProperties.isExternalTimeSeriesStore()) {
            throw new IllegalStateException(
                    "ispf.variable-history store falls back to ISPF_DB JDBC on MSSQL (B2, ADR-0078). "
                            + "Set ISPF_VARIABLE_HISTORY_STORE=clickhouse (or cassandra/scylla), "
                            + "ISPF_VARIABLE_HISTORY_ENABLED=false for B1, "
                            + "or ISPF_MSSQL_SINGLE_ENGINE=true for the opt-in JDBC profile."
            );
        }
        if (eventJournalProperties.isEnabled() && eventJournalProperties.isJdbcStore()) {
            throw new IllegalStateException(
                    "ispf.event-journal store falls back to ISPF_DB JDBC on MSSQL (B2, ADR-0078). "
                            + "Set ISPF_EVENT_JOURNAL_STORE=clickhouse (or cassandra/scylla), "
                            + "ISPF_EVENT_JOURNAL_ENABLED=false for B1, "
                            + "or ISPF_MSSQL_SINGLE_ENGINE=true for the opt-in JDBC profile."
            );
        }
    }

    private static void assertSingleEngineStores(
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            boolean warmEnabled,
            boolean coldArchiveEnabled
    ) {
        if (warmEnabled) {
            throw new IllegalStateException(
                    "MSSQL single-engine refuses ClickHouse warm-tier routing. "
                            + "Set ISPF_HISTORIAN_DEPLOY_PROFILE=mssql-single-engine "
                            + "and ISPF_HISTORIAN_TIER_WARM_ENABLED=false."
            );
        }
        if (coldArchiveEnabled) {
            throw new IllegalStateException(
                    "MSSQL single-engine refuses cold Parquet archive. "
                            + "Set ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=false."
            );
        }
        if (variableHistoryProperties.getSpool() != null && variableHistoryProperties.getSpool().isEnabled()) {
            throw new IllegalStateException(
                    "ISPF_VARIABLE_HISTORY_SPOOL_ENABLED is the ClickHouse spool. "
                            + "On MSSQL set it false. The JDBC file buffer is ISPF_MSSQL_JDBC_BUFFER."
            );
        }
        if (variableHistoryProperties.isEnabled()) {
            String store = variableHistoryProperties.getStore() == null
                    ? ""
                    : variableHistoryProperties.getStore().trim();
            if (variableHistoryProperties.isExternalTimeSeriesStore() || !"jdbc".equalsIgnoreCase(store)) {
                throw new IllegalStateException(
                        "MSSQL single-engine requires ISPF_VARIABLE_HISTORY_STORE=jdbc "
                                + "(got '" + store + "'; ClickHouse/Cassandra/jpa and typos are refused). "
                                + "Gate C1 partitions use PRIMARY KEY (sampled_at, id); "
                                + "VariableSampleEntity keeps a single @Id for PostgreSQL."
                );
            }
        }
        if (eventJournalProperties.isEnabled() && !eventJournalProperties.isJdbcStore()) {
            throw new IllegalStateException(
                    "MSSQL single-engine requires ISPF_EVENT_JOURNAL_STORE=jdbc."
            );
        }
    }
}
