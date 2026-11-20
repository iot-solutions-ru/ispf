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
            ClusterProperties clusterProperties
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
        // B2 jdbc history/journal (including dual-write's jdbc primary) and active-active
        // are allowed. RLS and HARD stay refused. Cluster still fail-closes without NATS
        // JetStream (and Redis when live mirror is on) via ClusterDependencyValidator.
    }
}
