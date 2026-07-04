package com.ispf.server.storage;

/**
 * Validates ClickHouse SQL identifiers before embedding them in DDL.
 */
public final class ClickHouseSchemaBootstrap {

    private ClickHouseSchemaBootstrap() {
    }

    public static String requireValidIdentifier(String identifier, String label) {
        if (identifier == null || !identifier.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid ClickHouse " + label + ": " + identifier);
        }
        return identifier;
    }

    public static String qualifiedTable(String database, String table) {
        return requireValidIdentifier(database, "database")
                + "."
                + requireValidIdentifier(table, "table");
    }
}
