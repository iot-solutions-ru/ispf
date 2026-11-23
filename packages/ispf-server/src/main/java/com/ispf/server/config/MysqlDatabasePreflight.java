package com.ispf.server.config;

/**
 * READ-COMMITTED and {@code utf8mb4_0900_bin} checks for the MySQL single-engine profile (ADR-0079 B2).
 * Pure so tests do not need a live MySQL server.
 */
public final class MysqlDatabasePreflight {

    static final String REQUIRED_ISOLATION = "READ-COMMITTED";
    static final String REQUIRED_COLLATION = "utf8mb4_0900_bin";

    private MysqlDatabasePreflight() {
    }

    public static void assertReady(String transactionIsolation, String databaseCollation) {
        if (!REQUIRED_ISOLATION.equalsIgnoreCase(trim(transactionIsolation))) {
            throw new IllegalStateException(
                    "MySQL single-engine requires transaction_isolation=READ-COMMITTED (ADR-0079), "
                            + "the isolation the gates ran. Actual: " + transactionIsolation + ". "
                            + "Start the server with --transaction-isolation=READ-COMMITTED."
            );
        }
        if (!REQUIRED_COLLATION.equalsIgnoreCase(trim(databaseCollation))) {
            throw new IllegalStateException(
                    "MySQL single-engine requires the ISPF database on utf8mb4_0900_bin "
                            + "(case-sensitive object paths, ADR-0079). Actual: " + databaseCollation
            );
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
