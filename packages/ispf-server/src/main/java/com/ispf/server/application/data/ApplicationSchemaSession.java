package com.ispf.server.application.data;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

@Component
public class ApplicationSchemaSession {

    private final DataSource dataSource;

    public ApplicationSchemaSession(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Creates application schema if missing — call from writable transactions (migrations/deploy). */
    public void ensureSchemaExists(String schemaName) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            createSchemaIfMissing(connection, schemaName);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to ensure application schema: " + schemaName, ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    public void runInSchema(String schemaName, Runnable action) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        String previousSchema = null;
        RuntimeException actionError = null;
        try {
            previousSchema = currentSchema(connection);
            activateSchema(connection, schemaName);
            try {
                action.run();
            } catch (RuntimeException ex) {
                actionError = ex;
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to activate application schema: " + schemaName, ex);
        } finally {
            try {
                rollbackIfNeeded(connection, actionError);
                restoreSchema(connection, previousSchema);
            } catch (SQLException ex) {
                if (actionError != null) {
                    actionError.addSuppressed(ex);
                    throw actionError;
                }
                throw new IllegalStateException("Failed to restore database schema", ex);
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        }
    }

    public void runWithPlatformCatalog(Runnable action) {
        callWithPlatformCatalog(() -> {
            action.run();
            return null;
        });
    }

    public <T> T callWithPlatformCatalog(Supplier<T> action) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        RuntimeException actionError = null;
        try {
            String previousSchema = currentSchema(connection);
            try {
                resetSchema(connection);
                try {
                    return action.get();
                } catch (RuntimeException ex) {
                    actionError = ex;
                    throw ex;
                }
            } finally {
                try {
                    rollbackIfNeeded(connection, actionError);
                    if (previousSchema != null && !previousSchema.isBlank()
                            && !"PUBLIC".equalsIgnoreCase(previousSchema)) {
                        activateSchema(connection, previousSchema);
                    }
                } catch (SQLException ex) {
                    if (actionError != null) {
                        actionError.addSuppressed(ex);
                        throw actionError;
                    }
                    throw new IllegalStateException("Failed to restore application schema", ex);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to access platform catalog", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static String currentSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException ex) {
            return null;
        }
    }

    private static void activateSchema(Connection connection, String schemaName) throws SQLException {
        String quoted = ApplicationSchemaSupport.quoteIdentifier(schemaName);
        try (Statement statement = connection.createStatement()) {
            // PostgreSQL report/function paths use read-only transactions — never run DDL there.
            if (!isPostgreSql(connection)) {
                createSchemaIfMissing(connection, schemaName);
            }
            if (isPostgreSql(connection)) {
                statement.execute("SET search_path TO " + quoted);
            } else {
                statement.execute("SET SCHEMA " + quoted);
            }
        }
    }

    private static void restoreSchema(Connection connection, String previousSchema) throws SQLException {
        if (previousSchema != null && !previousSchema.isBlank()) {
            activateSchema(connection, previousSchema);
        } else {
            resetSchema(connection);
        }
    }

    private static void createSchemaIfMissing(Connection connection, String schemaName) throws SQLException {
        String quoted = ApplicationSchemaSupport.quoteIdentifier(schemaName);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + quoted);
        }
    }

    private static void resetSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (isPostgreSql(connection)) {
                statement.execute("SET search_path TO public");
            } else {
                statement.execute("SET SCHEMA PUBLIC");
            }
        }
    }

    private static void rollbackIfNeeded(Connection connection, RuntimeException actionError) {
        if (actionError == null) {
            return;
        }
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException ignored) {
            // Best effort — restore may still fail on a broken connection.
        }
    }

    private static boolean isPostgreSql(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        return product.contains("postgresql");
    }
}
