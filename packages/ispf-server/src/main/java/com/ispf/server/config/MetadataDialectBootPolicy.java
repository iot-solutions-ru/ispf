package com.ispf.server.config;

import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Shared ADR-0078 / 0079 / 0080 / 0037 boot fences. Invoked before Flyway locations are applied and again
 * on {@code ContextRefreshedEvent} so disallowed engines never migrate.
 */
public final class MetadataDialectBootPolicy {

    public static final String ORACLE_JDBC_DRIVER = "oracle.jdbc.OracleDriver";
    static final String ORACLE_B1_BASELINE = "db/migration/oracle/V1__baseline.sql";

    private MetadataDialectBootPolicy() {
    }

    /**
     * Engine gate. Oracle (ADR-0080; Prod Yes B1 on Oracle Database Free 23.26.x only) boots only when
     * this build carries its B1 Flyway pack and the operator put the Oracle JDBC driver on the classpath;
     * ISPF never ships it.
     * MySQL, MSSQL and Oracle then boot only under their B1 fences, which every caller also runs.
     */
    public static void assertEngineCertified(RelationalDialect dialect) {
        assertEngineCertified(
                dialect,
                className -> ClassUtils.isPresent(className, MetadataDialectBootPolicy.class.getClassLoader()),
                path -> new ClassPathResource(path, MetadataDialectBootPolicy.class.getClassLoader()).exists()
        );
    }

    static void assertEngineCertified(
            RelationalDialect dialect,
            Predicate<String> classPresent,
            Predicate<String> resourcePresent
    ) {
        if (dialect.kind() != RelationalDbKind.ORACLE) {
            return;
        }
        if (!resourcePresent.test(ORACLE_B1_BASELINE)) {
            throw new IllegalStateException(
                    "ISPF_METADATA_DB_KIND=oracle needs the Oracle B1 Flyway pack (classpath:"
                            + ORACLE_B1_BASELINE + "), which this build does not contain (ADR-0080). "
                            + "Use postgresql, h2, mssql, or mysql. "
                            + "External DATA_SOURCE JDBC to Oracle for reports remains supported."
            );
        }
        if (!classPresent.test(ORACLE_JDBC_DRIVER)) {
            throw new IllegalStateException(oracleDriverMissingMessage());
        }
    }

    public static String oracleDriverMissingMessage() {
        return "ISPF_METADATA_DB_KIND=oracle needs the Oracle JDBC thin driver (" + ORACLE_JDBC_DRIVER
                + ", for example ojdbc17.jar) on the classpath. ISPF does not ship it (ADR-0080). "
                + "Put the JAR in a lib directory and start ISPF with "
                + "java -Dloader.path=lib -cp ispf-server.jar "
                + "org.springframework.boot.loader.launch.PropertiesLauncher";
    }

    /**
     * MySQL fences (ADR-0079). B1 keeps JDBC history/journal off ISPF_DB. The opt-in single-engine
     * profile ({@code ISPF_MYSQL_SINGLE_ENGINE}, B2 — § Amendment 5) allows both under the MSSQL
     * single-engine store rules. Cluster is allowed only with single-engine (A-A — § Amendment 6), the
     * cell the dual-JVM gate ran; B1 with cluster stays refused. Row isolation, {@code HARD} and
     * dual-write stay refused either way. These fences are the only thing between a MySQL ISPF_DB and an
     * uncertified mode; keep them before Flyway and on context refresh.
     */
    public static void assertMysqlB1Fences(
            RelationalDialect dialect,
            TenantIsolationProperties tenantIsolationProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            ClusterProperties clusterProperties,
            boolean singleEngine,
            boolean warmEnabled,
            boolean coldArchiveEnabled
    ) {
        if (dialect.kind() != RelationalDbKind.MYSQL) {
            // The flag clamps historian retention wherever it is set; keep it off other engines.
            if (singleEngine) {
                throw new IllegalStateException(
                        "ISPF_MYSQL_SINGLE_ENGINE=true applies to a MySQL ISPF_DB only (ADR-0079); "
                                + "this ISPF_DB is " + dialect.kind() + ". Unset it."
                );
            }
            return;
        }
        if (tenantIsolationProperties.isDbRowIsolation()) {
            throw new IllegalStateException(
                    "ispf.tenant.db-row-isolation=true is unsupported on MySQL (ADR-0079). "
                            + "Set ISPF_TENANT_DB_ROW_ISOLATION=false for single-tenant B1, "
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
                            + "B1 keeps history off ISPF_DB JDBC; single-engine keeps one JDBC store."
            );
        }
        // A-A (§ Amendment 6): cluster runs on the single-engine cell only. ClusterDependencyValidator
        // still fail-closes without NATS JetStream (+ Redis when live sync / path interest is on).
        // Residual tech-debt: failover under load / multi-hour soak — docs/en/parked-backlog.md P-MYSQL-AA-SOAK.
        if (clusterProperties.enabled() && !singleEngine) {
            throw new IllegalStateException(
                    "ispf.cluster.enabled=true on MySQL requires ISPF_MYSQL_SINGLE_ENGINE=true (ADR-0079 A-A): "
                            + "the dual-JVM gate ran only with history and journal on this database. "
                            + "Set the flag with the JDBC stores, or run a single JVM."
            );
        }
        if (singleEngine) {
            assertSingleEngineStores(
                    MYSQL_SINGLE_ENGINE, variableHistoryProperties, eventJournalProperties, warmEnabled, coldArchiveEnabled);
            return;
        }
        if (variableHistoryProperties.isEnabled() && !variableHistoryProperties.isExternalTimeSeriesStore()) {
            throw new IllegalStateException(
                    "ispf.variable-history store falls back to ISPF_DB JDBC on MySQL (B2, ADR-0079). "
                            + "Set ISPF_VARIABLE_HISTORY_STORE=clickhouse (or cassandra/scylla), "
                            + "ISPF_VARIABLE_HISTORY_ENABLED=false for B1, "
                            + "or ISPF_MYSQL_SINGLE_ENGINE=true for the opt-in JDBC profile."
            );
        }
        if (eventJournalProperties.isEnabled() && eventJournalProperties.isJdbcStore()) {
            throw new IllegalStateException(
                    "ispf.event-journal store falls back to ISPF_DB JDBC on MySQL (B2, ADR-0079). "
                            + "Set ISPF_EVENT_JOURNAL_STORE=clickhouse (or cassandra/scylla), "
                            + "ISPF_EVENT_JOURNAL_ENABLED=false for B1, "
                            + "or ISPF_MYSQL_SINGLE_ENGINE=true for the opt-in JDBC profile."
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
        // A-A on MSSQL is Prod Yes (ADR-0078 Phase 6): dialect no longer refuses cluster.
        // ClusterDependencyValidator still fail-closes without NATS JetStream (+ Redis when live sync).
        // Residual tech-debt: AG failover soak / dual-JVM chaos journal — docs/en/parked-backlog.md P-MSSQL-AA-SOAK.
        Objects.requireNonNull(clusterProperties, "clusterProperties");
        if (singleEngine) {
            assertSingleEngineStores(
                    MSSQL_SINGLE_ENGINE, variableHistoryProperties, eventJournalProperties, warmEnabled, coldArchiveEnabled);
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

    /** Engine wording for {@link #assertSingleEngineStores}; the store rules are shared. */
    private record SingleEngineProfile(String engine, String deployProfile, String spoolHint, String storeHint) {
    }

    private static final SingleEngineProfile MSSQL_SINGLE_ENGINE = new SingleEngineProfile(
            "MSSQL",
            "mssql-single-engine",
            "On MSSQL set it false. The JDBC file buffer is ISPF_MSSQL_JDBC_BUFFER.",
            "Gate C1 partitions use PRIMARY KEY (sampled_at, id); "
                    + "VariableSampleEntity keeps a single @Id for PostgreSQL."
    );

    private static final SingleEngineProfile MYSQL_SINGLE_ENGINE = new SingleEngineProfile(
            "MySQL",
            "mysql-single-engine",
            "On MySQL set it false. MySQL single-engine has no file buffer in front of ISPF_DB.",
            ""
    );

    private static void assertSingleEngineStores(
            SingleEngineProfile profile,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            boolean warmEnabled,
            boolean coldArchiveEnabled
    ) {
        if (warmEnabled) {
            throw new IllegalStateException(
                    profile.engine() + " single-engine refuses ClickHouse warm-tier routing. "
                            + "Set ISPF_HISTORIAN_DEPLOY_PROFILE=" + profile.deployProfile() + " "
                            + "and ISPF_HISTORIAN_TIER_WARM_ENABLED=false."
            );
        }
        if (coldArchiveEnabled) {
            throw new IllegalStateException(
                    profile.engine() + " single-engine refuses cold Parquet archive. "
                            + "Set ISPF_HISTORIAN_COLD_ARCHIVE_ENABLED=false."
            );
        }
        if (variableHistoryProperties.getSpool() != null && variableHistoryProperties.getSpool().isEnabled()) {
            throw new IllegalStateException(
                    "ISPF_VARIABLE_HISTORY_SPOOL_ENABLED is the ClickHouse spool. " + profile.spoolHint()
            );
        }
        if (variableHistoryProperties.isEnabled()) {
            String store = variableHistoryProperties.getStore() == null
                    ? ""
                    : variableHistoryProperties.getStore().trim();
            if (variableHistoryProperties.isExternalTimeSeriesStore() || !"jdbc".equalsIgnoreCase(store)) {
                throw new IllegalStateException(
                        profile.engine() + " single-engine requires ISPF_VARIABLE_HISTORY_STORE=jdbc "
                                + "(got '" + store + "'; ClickHouse/Cassandra/jpa and typos are refused)."
                                + (profile.storeHint().isEmpty() ? "" : " " + profile.storeHint())
                );
            }
        }
        if (eventJournalProperties.isEnabled() && !eventJournalProperties.isJdbcStore()) {
            throw new IllegalStateException(
                    profile.engine() + " single-engine requires ISPF_EVENT_JOURNAL_STORE=jdbc."
            );
        }
    }

    /**
     * Oracle B1 fences (ADR-0080; Prod Yes B1 on Oracle Database Free 23.26.x only, signed with these
     * fences on). No single-engine bypass — JDBC history/journal on
     * ISPF_DB is a later B2 decision. These fences are the only thing between an Oracle ISPF_DB and
     * an uncertified mode; keep them before Flyway and on context refresh.
     */
    public static void assertOracleB1Fences(
            RelationalDialect dialect,
            TenantIsolationProperties tenantIsolationProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            ClusterProperties clusterProperties
    ) {
        if (dialect.kind() != RelationalDbKind.ORACLE) {
            return;
        }
        if (tenantIsolationProperties.isDbRowIsolation()) {
            throw new IllegalStateException(
                    "ispf.tenant.db-row-isolation=true is unsupported on Oracle (ADR-0080). "
                            + "Set ISPF_TENANT_DB_ROW_ISOLATION=false for single-tenant B1, "
                            + "or use PostgreSQL until a native Virtual Private Database policy exists."
            );
        }
        if (tenantIsolationProperties.isHardMode()) {
            throw new IllegalStateException(
                    "ispf.tenant.isolation-mode=HARD is unsupported on Oracle (ADR-0080). "
                            + "Per-tenant schemas require PostgreSQL search_path."
            );
        }
        if (variableHistoryProperties.isDualWriteEnabled()) {
            throw new IllegalStateException(
                    "ispf.variable-history.dual-write-enabled=true is refused on Oracle (ADR-0080). "
                            + "B1 keeps history off ISPF_DB JDBC."
            );
        }
        if (clusterProperties.enabled()) {
            throw new IllegalStateException(
                    "ispf.cluster.enabled=true is not certified on Oracle (ADR-0080). "
                            + "Run a single JVM, or use PostgreSQL for active-active."
            );
        }
        if (variableHistoryProperties.isEnabled() && !variableHistoryProperties.isExternalTimeSeriesStore()) {
            throw new IllegalStateException(
                    "ispf.variable-history store falls back to ISPF_DB JDBC on Oracle (B2, ADR-0080). "
                            + "Set ISPF_VARIABLE_HISTORY_STORE=clickhouse (or cassandra/scylla), "
                            + "or ISPF_VARIABLE_HISTORY_ENABLED=false for B1."
            );
        }
        if (eventJournalProperties.isEnabled() && eventJournalProperties.isJdbcStore()) {
            throw new IllegalStateException(
                    "ispf.event-journal store falls back to ISPF_DB JDBC on Oracle (B2, ADR-0080). "
                            + "Set ISPF_EVENT_JOURNAL_STORE=clickhouse (or cassandra/scylla), "
                            + "or ISPF_EVENT_JOURNAL_ENABLED=false for B1."
            );
        }
    }
}
