package com.ispf.server.application.data;

import com.ispf.server.relational.RelationalDialect;
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
    private final RelationalDialect dialect;

    public ApplicationSchemaSession(DataSource dataSource, RelationalDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    /**
     * Creates application schema if missing. Uses a connection from the pool without relying on the
     * caller's read-only transaction (report previews are {@code readOnly=true}).
     */
    public void ensureSchemaExists(String schemaName) {
        try (Connection connection = dataSource.getConnection()) {
            createSchemaIfMissing(connection, schemaName);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to ensure application schema: " + schemaName, ex);
        }
    }

    public void runInSchema(String schemaName, Runnable action) {
        ensureSchemaExists(schemaName);
        Connection connection = DataSourceUtils.getConnection(dataSource);
        String previousSchema = null;
        RuntimeException actionError = null;
        try {
            previousSchema = currentSchema(connection);
            // Never CREATE on the transactional connection — may be read-only (reports).
            switchSearchPath(connection, schemaName);
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
                            && !dialect.defaultPlatformSchema().equalsIgnoreCase(previousSchema)) {
                        // Restore only — never CREATE SCHEMA (may be inside read-only tx).
                        switchSearchPath(connection, previousSchema);
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

    private void restoreSchema(Connection connection, String previousSchema) throws SQLException {
        if (previousSchema != null && !previousSchema.isBlank()) {
            switchSearchPath(connection, previousSchema);
        } else {
            resetSchema(connection);
        }
    }

    private void switchSearchPath(Connection connection, String schemaName) throws SQLException {
        String quoted = ApplicationSchemaSupport.quoteIdentifier(schemaName);
        try (Statement statement = connection.createStatement()) {
            statement.execute(dialect.activateSchemaSql(quoted));
        }
    }

    private void createSchemaIfMissing(Connection connection, String schemaName) throws SQLException {
        String quoted = ApplicationSchemaSupport.quoteIdentifier(schemaName);
        try (Statement statement = connection.createStatement()) {
            statement.execute(dialect.createSchemaIfNotExistsSql(quoted));
        }
    }

    private void resetSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(dialect.resetPlatformSchemaSql());
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
}
