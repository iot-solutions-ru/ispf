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

    // Error Prone [Finally]: the finally block deliberately rethrows the *same* action error (with the
    // restore failure attached as suppressed) or, when the action succeeded, surfaces the restore failure.
    // Nothing thrown by the try block is masked.
    @SuppressWarnings("Finally")
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

    /**
     * Runs {@code work} on one connection with auto-commit off, after switching to {@code schemaName}.
     * Commits only when {@code work} returns. A failure rolls the connection back, including DDL.
     * JdbcTemplate is not used: its execute path was committing each statement on its own.
     */
    public void runInSchemaTransaction(String schemaName, SchemaSqlWork work) {
        ensureSchemaExists(schemaName);
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            String previousSchema = null;
            try {
                previousSchema = currentSchema(connection);
                // Commit the schema switch before the migration transaction. A later rollback must not
                // send an unqualified statement back to the platform schema.
                switchSearchPath(connection, schemaName);
                connection.setAutoCommit(false);
                work.run(connection, schemaName);
                connection.commit();
            } catch (Exception ex) {
                rollbackQuietly(connection);
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(
                        ex.getMessage() != null ? ex.getMessage() : "Migration SQL failed",
                        ex
                );
            } finally {
                restoreSchemaQuietly(connection, previousSchema);
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // The connection is about to be closed.
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to open migration transaction for schema: " + schemaName, ex);
        }
    }

    public void runWithPlatformCatalog(Runnable action) {
        callWithPlatformCatalog(() -> {
            action.run();
            return null;
        });
    }

    @SuppressWarnings("Finally") // same rethrow-with-suppressed pattern as runInSchema
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
        if (previousSchema != null && !previousSchema.isBlank()
                && !dialect.defaultPlatformSchema().equalsIgnoreCase(previousSchema)) {
            switchSearchPath(connection, previousSchema);
        } else {
            resetSchema(connection);
        }
    }

    private void switchSearchPath(Connection connection, String schemaName) throws SQLException {
        // Allowlisted identifier — dialect SQL may include companion schemas (e.g. ", public").
        final String safeSchema = ApplicationSchemaSupport.quoteIdentifier(schemaName);
        try (Statement statement = connection.createStatement()) {
            // Schema name is allowlisted in quoteIdentifier (^[A-Za-z_][A-Za-z0-9_]*$).
            // codeql[java/sql-injection]
            statement.execute(dialect.activateSchemaSql(safeSchema));
        }
    }

    private void createSchemaIfMissing(Connection connection, String schemaName) throws SQLException {
        final String safeSchema = ApplicationSchemaSupport.quoteIdentifier(schemaName);
        try (Statement statement = connection.createStatement()) {
            // Schema name is allowlisted in quoteIdentifier (^[A-Za-z_][A-Za-z0-9_]*$).
            // codeql[java/sql-injection]
            statement.execute(dialect.createSchemaIfNotExistsSql(safeSchema));
        }
    }

    private void resetSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(dialect.resetPlatformSchemaSql());
        }
    }

    private void restoreSchemaQuietly(Connection connection, String previousSchema) {
        try {
            restoreSchema(connection, previousSchema);
        } catch (SQLException ignored) {
            // Best effort after commit or rollback.
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Best effort — the caller still surfaces the original statement error.
        }
    }

    @FunctionalInterface
    public interface SchemaSqlWork {
        void run(Connection connection, String schemaName) throws SQLException;
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
