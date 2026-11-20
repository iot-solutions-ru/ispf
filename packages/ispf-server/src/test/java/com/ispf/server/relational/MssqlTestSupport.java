package com.ispf.server.relational;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Shared helpers for ADR-0078 MSSQL Testcontainers gates.
 * <p>RCSI is mandatory for B1 (ADR-0078): without it, Hibernate {@code REQUIRES_NEW}
 * inserts block forever on {@code object_nodes} while an outer TX holds X locks.
 * Testcontainers defaults to {@code master}, which cannot take RCSI — create {@code ispf}.
 */
final class MssqlTestSupport {

    static final String ISPF_DB = "ispf";

    private MssqlTestSupport() {
    }

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            return false;
        }
    }

    /**
     * Create {@code ispf} DB, enable RCSI, return JDBC URL pointing at it.
     * Call once after {@link MSSQLServerContainer#start()} and before Spring / Hikari start.
     */
    static String prepareIspfDatabase(MSSQLServerContainer mssql) {
        try (Connection connection = DriverManager.getConnection(
                mssql.getJdbcUrl(), mssql.getUsername(), mssql.getPassword());
             Statement statement = connection.createStatement()) {
            // ADR-0078 §6: named case-sensitive collation for object paths (lab/CI signal).
            statement.execute(
                    "IF DB_ID(N'" + ISPF_DB + "') IS NULL "
                            + "CREATE DATABASE [" + ISPF_DB + "] COLLATE Latin1_General_CS_AS"
            );
            statement.execute("ALTER DATABASE [" + ISPF_DB + "] SET ALLOW_SNAPSHOT_ISOLATION ON");
            statement.execute(
                    "ALTER DATABASE [" + ISPF_DB + "] SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE"
            );
            try (ResultSet rs = statement.executeQuery(
                    "SELECT is_read_committed_snapshot_on FROM sys.databases WHERE name = N'" + ISPF_DB + "'")) {
                rs.next();
                if (!rs.getBoolean(1)) {
                    throw new IllegalStateException("RCSI failed to enable on database " + ISPF_DB);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to prepare MSSQL test database '" + ISPF_DB + "' with RCSI", ex);
        }
        return jdbcUrlForIspf(mssql);
    }

    static String jdbcUrlForIspf(MSSQLServerContainer mssql) {
        String base = mssql.getJdbcUrl();
        if (base.toLowerCase().contains("databasename=")) {
            return base.replaceAll("(?i)databaseName=[^;]*", "databaseName=" + ISPF_DB);
        }
        String sep = base.endsWith(";") ? "" : ";";
        return base + sep + "databaseName=" + ISPF_DB;
    }
}
