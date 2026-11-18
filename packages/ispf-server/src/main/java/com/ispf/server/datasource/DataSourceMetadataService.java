package com.ispf.server.datasource;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Schema browser for a data source. Uses JDBC {@link DatabaseMetaData} so one path covers the
 * internal PostgreSQL schema and allowlisted external drivers. DDL is internal-only.
 */
@Service
public class DataSourceMetadataService {

    static final int MAX_SCHEMAS = 40;
    static final int MAX_TABLES = 400;
    static final int MAX_COLUMNS = 32;

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");
    private static final Pattern META_NAME = Pattern.compile("^[^\\p{Cc}%\"'`;\\\\]{1,128}$");
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema",
            "sys",
            "mysql",
            "performance_schema",
            "system",
            "sysibm",
            "syscat",
            "sysstat"
    );

    private final DataSourceObjectService dataSourceObjectService;
    private final ObjectManager objectManager;
    private final DataSourceSqlSession dataSourceSqlSession;
    private final DataSourceConnectionResolver connectionResolver;
    private final DataSourceQueryExecutor queryExecutor;
    private final TenantLocalDataAccessGuard tenantLocalDataAccessGuard;

    public DataSourceMetadataService(
            DataSourceObjectService dataSourceObjectService,
            ObjectManager objectManager,
            DataSourceSqlSession dataSourceSqlSession,
            DataSourceConnectionResolver connectionResolver,
            DataSourceQueryExecutor queryExecutor,
            TenantLocalDataAccessGuard tenantLocalDataAccessGuard
    ) {
        this.dataSourceObjectService = dataSourceObjectService;
        this.objectManager = objectManager;
        this.dataSourceSqlSession = dataSourceSqlSession;
        this.connectionResolver = connectionResolver;
        this.queryExecutor = queryExecutor;
        this.tenantLocalDataAccessGuard = tenantLocalDataAccessGuard;
    }

    public DataSourceCatalog catalog(String path) {
        requireDataSourceForRead(path);
        boolean external = connectionResolver.isExternal(path);
        return dataSourceSqlSession.callForMetadata(path, template -> template.execute((Connection connection) -> {
            DatabaseMetaData meta = connection.getMetaData();
            if (!external) {
                String schema = connectionResolver.resolveSchemaName(path);
                SchemaNode node = readSchema(meta, null, schema, schema);
                return new DataSourceCatalog(
                        false,
                        false,
                        productName(meta),
                        identifierQuote(meta),
                        schema,
                        List.of(node)
                );
            }
            return readExternalCatalog(connection, meta);
        }));
    }

    public List<ColumnView> columns(String path, String catalog, String schema, String table) {
        requireDataSourceForRead(path);
        String tableName = requireMetaName(table, "table");
        boolean external = connectionResolver.isExternal(path);
        String schemaName = blankToNull(schema);
        String catalogName = blankToNull(catalog);
        if (!external) {
            String expected = connectionResolver.resolveSchemaName(path);
            if (schemaName != null && !schemaName.equalsIgnoreCase(expected)) {
                throw new IllegalArgumentException("Schema is outside this data source");
            }
            schemaName = expected;
            catalogName = null;
        } else if (schemaName == null && catalogName == null) {
            throw new IllegalArgumentException("schema or catalog is required");
        }
        if (schemaName != null) {
            schemaName = requireMetaName(schemaName, "schema");
        }
        if (catalogName != null) {
            catalogName = requireMetaName(catalogName, "catalog");
        }
        String resolvedSchema = schemaName;
        String resolvedCatalog = catalogName;
        return dataSourceSqlSession.callForMetadata(path, template -> template.execute((Connection connection) ->
                readColumns(connection.getMetaData(), resolvedCatalog, resolvedSchema, tableName)));
    }

    public CreatedTable createTable(String path, String table, List<ColumnSpec> columns) {
        requireDataSourceForWrite(path);
        if (connectionResolver.isExternal(path)) {
            throw new IllegalArgumentException("DDL is not allowed on external data sources");
        }
        String schema = connectionResolver.resolveSchemaName(path);
        String tableName = requireIdent(table, "table");
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("At least one column is required");
        }
        if (columns.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("At most " + MAX_COLUMNS + " columns are allowed");
        }
        String sql = buildCreateTable(schema, tableName, columns);
        queryExecutor.execute(path, sql, List.of(), null);
        return new CreatedTable(schema, tableName);
    }

    /** Metadata GETs must not mutate object structure or create missing PG schemas. */
    private void requireDataSourceForRead(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.DATA_SOURCE) {
            throw new IllegalArgumentException("Not a data source object: " + path);
        }
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(path);
    }

    private void requireDataSourceForWrite(String path) {
        dataSourceObjectService.ensureStructure(path);
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(path);
    }

    private DataSourceCatalog readExternalCatalog(Connection connection, DatabaseMetaData meta) throws SQLException {
        List<SchemaRef> refs = listExternalSchemas(connection, meta);
        boolean schemasTruncated = refs.size() > MAX_SCHEMAS;
        if (schemasTruncated) {
            refs = refs.subList(0, MAX_SCHEMAS);
        }
        List<SchemaNode> schemas = new ArrayList<>();
        for (SchemaRef ref : refs) {
            schemas.add(readSchema(meta, ref.catalog(), ref.schema(), ref.name()));
        }
        return new DataSourceCatalog(
                true,
                schemasTruncated,
                productName(meta),
                identifierQuote(meta),
                null,
                schemas
        );
    }

    private List<SchemaRef> listExternalSchemas(Connection connection, DatabaseMetaData meta) throws SQLException {
        List<SchemaRef> refs = new ArrayList<>();
        try (ResultSet rs = meta.getSchemas()) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                if (schema == null || schema.isBlank() || isSystemSchema(schema)) {
                    continue;
                }
                refs.add(new SchemaRef(null, schema, schema));
            }
        } catch (SQLException ignored) {
            refs.clear();
        }
        if (refs.isEmpty()) {
            try (ResultSet rs = meta.getCatalogs()) {
                while (rs.next()) {
                    String catalog = rs.getString("TABLE_CAT");
                    if (catalog == null || catalog.isBlank() || isSystemSchema(catalog)) {
                        continue;
                    }
                    refs.add(new SchemaRef(catalog, null, catalog));
                }
            }
        }
        if (refs.isEmpty()) {
            String schema = connection.getSchema();
            if (schema != null && !schema.isBlank() && !isSystemSchema(schema)) {
                refs.add(new SchemaRef(null, schema, schema));
            }
        }
        return refs;
    }

    private SchemaNode readSchema(DatabaseMetaData meta, String catalog, String schema, String name) throws SQLException {
        List<TableNode> tables = readTables(meta, catalog, schema);
        boolean truncated = tables.size() > MAX_TABLES;
        if (truncated) {
            tables = tables.subList(0, MAX_TABLES);
        }
        return new SchemaNode(name, catalog, schema, truncated, tables);
    }

    private List<TableNode> readTables(DatabaseMetaData meta, String catalog, String schema) throws SQLException {
        String useCatalog = schema == null || schema.isBlank() ? catalog : null;
        String useSchema = schema == null || schema.isBlank() ? null : likeExact(meta, schema);
        List<TableNode> tables = new ArrayList<>();
        // Catalog/schema already META_NAME-validated; escape LIKE wildcards (_ / %).
        // DatabaseMetaData is not concatenated SQL — allowlisted names only.
        ResultSet tablesRs = meta.getTables(
                useCatalog, useSchema, "%", new String[]{"TABLE", "VIEW", "BASE TABLE"}); // codeql[java/sql-injection]
        try (tablesRs) {
            while (tablesRs.next()) {
                String tableName = tablesRs.getString("TABLE_NAME");
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                String type = normalizeTableType(tablesRs.getString("TABLE_TYPE"));
                if (type == null) {
                    continue;
                }
                tables.add(new TableNode(tableName, type));
            }
        }
        return tables;
    }

    private List<ColumnView> readColumns(
            DatabaseMetaData meta,
            String catalog,
            String schema,
            String table
    ) throws SQLException {
        String useCatalog = schema == null ? catalog : null;
        // Match the request against JDBC-reported names, then call getPrimaryKeys/getColumns only with
        // ResultSet-derived identifiers (not request params) so CodeQL does not treat them as SQL sinks.
        String resolvedSchema = null;
        String resolvedTable = null;
        String schemaFilter = schema == null ? null : likeExact(meta, schema);
        ResultSet tablesRs = meta.getTables(
                useCatalog, schemaFilter, "%", new String[]{"TABLE", "VIEW", "BASE TABLE"}); // codeql[java/sql-injection]
        try (tablesRs) {
            while (tablesRs.next()) {
                String reportedTable = tablesRs.getString("TABLE_NAME");
                if (reportedTable == null || !table.equals(reportedTable)) {
                    continue;
                }
                String reportedSchema = tablesRs.getString("TABLE_SCHEM");
                if (schema != null && reportedSchema != null && !schema.equalsIgnoreCase(reportedSchema)) {
                    continue;
                }
                resolvedSchema = reportedSchema;
                resolvedTable = reportedTable;
                break;
            }
        }
        if (resolvedTable == null) {
            return List.of();
        }
        Set<String> primaryKeys = new LinkedHashSet<>();
        try (ResultSet primaryKeysRs = meta.getPrimaryKeys(useCatalog, resolvedSchema, resolvedTable)) {
            while (primaryKeysRs.next()) {
                String column = primaryKeysRs.getString("COLUMN_NAME");
                if (column != null && !column.isBlank()) {
                    primaryKeys.add(column);
                }
            }
        }
        String resolvedSchemaPattern = resolvedSchema == null ? null : likeExact(meta, resolvedSchema);
        String resolvedTablePattern = likeExact(meta, resolvedTable);
        List<ColumnView> columns = new ArrayList<>();
        try (ResultSet columnsRs = meta.getColumns(
                useCatalog, resolvedSchemaPattern, resolvedTablePattern, "%")) {
            while (columnsRs.next()) {
                if (!resolvedTable.equals(columnsRs.getString("TABLE_NAME"))) {
                    continue;
                }
                String columnSchema = columnsRs.getString("TABLE_SCHEM");
                if (resolvedSchema != null && columnSchema != null
                        && !resolvedSchema.equalsIgnoreCase(columnSchema)) {
                    continue;
                }
                String name = columnsRs.getString("COLUMN_NAME");
                if (name == null || name.isBlank()) {
                    continue;
                }
                int size = columnsRs.getInt("COLUMN_SIZE");
                boolean nullable = columnsRs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                columns.add(new ColumnView(
                        name,
                        columnsRs.getString("TYPE_NAME"),
                        size,
                        nullable && !primaryKeys.contains(name),
                        primaryKeys.contains(name)
                ));
            }
        }
        return columns;
    }

    static String buildCreateTable(String schema, String table, List<ColumnSpec> columns) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(quoteIdent(schema)).append('.').append(quoteIdent(table)).append(" (");
        List<String> primaryKeys = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < columns.size(); i++) {
            ColumnSpec column = columns.get(i);
            if (column == null) {
                throw new IllegalArgumentException("Column is required");
            }
            String name = requireIdent(column.name(), "column");
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate column: " + name);
            }
            boolean primaryKey = Boolean.TRUE.equals(column.primaryKey());
            boolean notNull = primaryKey || Boolean.FALSE.equals(column.nullable());
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(quoteIdent(name)).append(' ').append(sqlType(column));
            if (notNull) {
                sql.append(" NOT NULL");
            }
            if (primaryKey) {
                primaryKeys.add(quoteIdent(name));
            }
        }
        if (!primaryKeys.isEmpty()) {
            sql.append(", PRIMARY KEY (");
            sql.append(String.join(", ", primaryKeys));
            sql.append(')');
        }
        sql.append(')');
        return sql.toString();
    }

    private static String sqlType(ColumnSpec column) {
        String type = column.type() == null ? "" : column.type().trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "INTEGER" -> "INTEGER";
            case "BIGINT" -> "BIGINT";
            case "BOOLEAN" -> "BOOLEAN";
            case "TEXT" -> "TEXT";
            case "VARCHAR" -> "VARCHAR(" + varcharLength(column.length()) + ")";
            case "NUMERIC" -> "NUMERIC";
            case "DOUBLE" -> "DOUBLE PRECISION";
            case "DATE" -> "DATE";
            case "TIMESTAMP" -> "TIMESTAMP";
            case "UUID" -> "UUID";
            default -> throw new IllegalArgumentException("Unsupported column type: " + column.type());
        };
    }

    private static int varcharLength(Integer length) {
        int value = length == null ? 255 : length;
        if (value < 1 || value > 4000) {
            throw new IllegalArgumentException("VARCHAR length must be between 1 and 4000");
        }
        return value;
    }

    private static String quoteIdent(String ident) {
        requireIdent(ident, "identifier");
        return "\"" + ident + "\"";
    }

    /** Escape {@code _} / {@code %} so JDBC {@code DatabaseMetaData} LIKE patterns match literally. */
    private static String likeExact(DatabaseMetaData meta, String value) throws SQLException {
        if (value == null) {
            return null;
        }
        String escape = meta.getSearchStringEscape();
        if (escape != null && escape.isEmpty()) {
            // Driver has no LIKE escape; cannot safely escape wildcards — return as-is.
            return value;
        }
        if (escape == null) {
            escape = "\\";
        }
        StringBuilder out = new StringBuilder(value.length() * 2);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '%' || ch == '_' || (escape.length() == 1 && ch == escape.charAt(0))) {
                out.append(escape);
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static String requireIdent(String value, String label) {
        if (value == null || !IDENT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return value;
    }

    private static String requireMetaName(String value, String label) {
        if (value == null || !META_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return value;
    }

    private static boolean isSystemSchema(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.startsWith("pg_") || SYSTEM_SCHEMAS.contains(normalized);
    }

    private static String normalizeTableType(String type) {
        if (type == null) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("VIEW".equals(normalized)) {
            return "VIEW";
        }
        if ("TABLE".equals(normalized) || "BASE TABLE".equals(normalized)) {
            return "TABLE";
        }
        return null;
    }

    private static String productName(DatabaseMetaData meta) {
        try {
            String name = meta.getDatabaseProductName();
            return name == null ? "" : name;
        } catch (SQLException ex) {
            return "";
        }
    }

    private static String identifierQuote(DatabaseMetaData meta) {
        try {
            String quote = meta.getIdentifierQuoteString();
            if (quote == null || quote.isBlank()) {
                return "\"";
            }
            return quote;
        } catch (SQLException ex) {
            return "\"";
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private record SchemaRef(String catalog, String schema, String name) {
    }

    public record DataSourceCatalog(
            boolean readOnly,
            boolean schemasTruncated,
            String productName,
            String identifierQuote,
            String defaultSchema,
            List<SchemaNode> schemas
    ) {
    }

    public record SchemaNode(
            String name,
            String catalog,
            String schema,
            boolean truncated,
            List<TableNode> tables
    ) {
    }

    public record TableNode(String name, String type) {
    }

    public record ColumnView(
            String name,
            String typeName,
            int size,
            boolean nullable,
            boolean primaryKey
    ) {
    }

    public record ColumnSpec(
            String name,
            String type,
            Integer length,
            Boolean nullable,
            Boolean primaryKey
    ) {
    }

    public record CreatedTable(String schema, String table) {
    }
}
