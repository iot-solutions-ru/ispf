package com.ispf.server.config;

import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;

/**
 * Shared ADR-0078 / 0037 boot fences. Invoked before Flyway locations are applied and again
 * on {@code ContextRefreshedEvent} so disallowed engines never migrate.
 */
public final class MetadataDialectBootPolicy {

    private MetadataDialectBootPolicy() {
    }

    public static void assertEngineCertified(RelationalDialect dialect) {
        RelationalDbKind kind = dialect.kind();
        if (kind == RelationalDbKind.MYSQL || kind == RelationalDbKind.ORACLE) {
            throw new IllegalStateException(
                    "ISPF_METADATA_DB_KIND=" + kind.name().toLowerCase()
                            + " is not certified (ADR-0037 tier 4 / ADR-0078). "
                            + "Use postgresql or h2, or mssql only under the ADR-0078 lab/B1 fences. "
                            + "External DATA_SOURCE JDBC to MySQL/Oracle for reports remains supported."
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
        if (variableHistoryProperties.isEnabled() && variableHistoryProperties.isExternalTimeSeriesStore()) {
            throw new IllegalStateException(
                    "MSSQL single-engine requires ISPF_VARIABLE_HISTORY_STORE=jdbc "
                            + "(ClickHouse/Cassandra stores are refused)."
            );
        }
        if (eventJournalProperties.isEnabled() && !eventJournalProperties.isJdbcStore()) {
            throw new IllegalStateException(
                    "MSSQL single-engine requires ISPF_EVENT_JOURNAL_STORE=jdbc."
            );
        }
    }
}
