package com.ispf.server.relational;

import com.ispf.server.config.MetadataDbProperties;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class RelationalDialectDetector {

    private final Map<RelationalDbKind, RelationalDialect> dialectsByKind;
    private final MetadataDbProperties metadataDbProperties;

    public RelationalDialectDetector(
            Map<RelationalDbKind, RelationalDialect> dialectsByKind,
            MetadataDbProperties metadataDbProperties
    ) {
        this.dialectsByKind = Map.copyOf(dialectsByKind);
        this.metadataDbProperties = metadataDbProperties;
    }

    public RelationalDialect resolve(DataSource dataSource) {
        RelationalDbKind configured = RelationalDbKind.fromConfig(metadataDbProperties.getKind());
        if (configured != null) {
            return require(configured);
        }
        if (dataSource == null) {
            throw new IllegalStateException("DataSource is required to detect relational database kind");
        }
        return require(detectFromDataSource(dataSource));
    }

    public RelationalDbKind detectFromDataSource(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return detectFromConnection(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to detect relational database kind", ex);
        }
    }

    static RelationalDbKind detectFromConnection(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        if (product.contains("postgresql")) {
            return RelationalDbKind.POSTGRESQL;
        }
        if (product.contains("h2")) {
            return RelationalDbKind.H2;
        }
        if (product.contains("microsoft")) {
            return RelationalDbKind.MSSQL;
        }
        if (product.contains("mysql") || product.contains("mariadb")) {
            return RelationalDbKind.MYSQL;
        }
        if (product.contains("oracle")) {
            return RelationalDbKind.ORACLE;
        }
        throw new IllegalStateException("Unsupported database product: " + product);
    }

    private RelationalDialect require(RelationalDbKind kind) {
        RelationalDialect dialect = dialectsByKind.get(kind);
        if (dialect == null) {
            throw new IllegalStateException("No RelationalDialect registered for " + kind);
        }
        return dialect;
    }
}
