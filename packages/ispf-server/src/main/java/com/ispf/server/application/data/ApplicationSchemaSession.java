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

    public void runInSchema(String schemaName, Runnable action) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            String previousSchema = currentSchema(connection);
            try {
                activateSchema(connection, schemaName);
                action.run();
            } finally {
                try {
                    if (previousSchema != null && !previousSchema.isBlank()) {
                        activateSchema(connection, previousSchema);
                    } else {
                        resetSchema(connection);
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException("Failed to restore database schema", ex);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to activate application schema: " + schemaName, ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
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
        try {
            String previousSchema = currentSchema(connection);
            try {
                resetSchema(connection);
                return action.get();
            } finally {
                try {
                    if (previousSchema != null && !previousSchema.isBlank()
                            && !"PUBLIC".equalsIgnoreCase(previousSchema)) {
                        activateSchema(connection, previousSchema);
                    }
                } catch (SQLException ex) {
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
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + quoted);
            if (isPostgreSql(connection)) {
                statement.execute("SET search_path TO " + quoted);
            } else {
                statement.execute("SET SCHEMA " + quoted);
            }
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

    private static boolean isPostgreSql(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        return product.contains("postgresql");
    }
}
