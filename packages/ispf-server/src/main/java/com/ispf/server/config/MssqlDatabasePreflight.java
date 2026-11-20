package com.ispf.server.config;

/**
 * RCSI and case-sensitive collation checks for the MSSQL single-engine profile.
 * Pure so tests do not need a live SQL Server.
 */
public final class MssqlDatabasePreflight {

    private MssqlDatabasePreflight() {
    }

    public static void assertReady(boolean readCommittedSnapshotOn, String collationName) {
        if (!readCommittedSnapshotOn) {
            throw new IllegalStateException(
                    "MSSQL single-engine requires READ_COMMITTED_SNAPSHOT ON (ADR-0078). "
                            + "Ask the DBA to enable RCSI on this database before boot."
            );
        }
        if (collationName == null || !collationName.toUpperCase().contains("_CS_")) {
            throw new IllegalStateException(
                    "MSSQL single-engine requires a case-sensitive collation (_CS_). Actual: "
                            + collationName
            );
        }
    }
}
