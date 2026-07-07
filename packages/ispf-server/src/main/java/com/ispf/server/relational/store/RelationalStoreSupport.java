package com.ispf.server.relational.store;

import com.ispf.server.relational.RelationalDialect;

/**
 * Shared JDBC store helpers for dialect-aware SQL (ADR-0037 store consolidation entry point).
 */
public final class RelationalStoreSupport {

    private RelationalStoreSupport() {
    }

    public static String platformTable(RelationalDialect dialect, String tableName) {
        return dialect.platformSchemaPrefix() + tableName;
    }
}
