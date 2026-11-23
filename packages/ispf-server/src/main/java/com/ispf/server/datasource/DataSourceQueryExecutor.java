package com.ispf.server.datasource;

import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataSourceQueryExecutor {

    public static final int DEFAULT_MAX_ROWS = 10_000;
    public static final int HARD_MAX_ROWS = 100_000;

    private final DataSourceSqlSession dataSourceSqlSession;
    private final DataSourceConnectionResolver connectionResolver;

    public DataSourceQueryExecutor(
            DataSourceSqlSession dataSourceSqlSession,
            DataSourceConnectionResolver connectionResolver
    ) {
        this.dataSourceSqlSession = dataSourceSqlSession;
        this.connectionResolver = connectionResolver;
    }

    public DataSourceQueryResult execute(String dataSourcePath, String sql, List<Object> params, Integer maxRows) {
        try {
            return executeOnConnection(dataSourcePath, sql, params, maxRows);
        } catch (BadSqlGrammarException ex) {
            throw new IllegalArgumentException(grammarMessage(ex), ex);
        }
    }

    private DataSourceQueryResult executeOnConnection(
            String dataSourcePath,
            String sql,
            List<Object> params,
            Integer maxRows
    ) {
        String query = DataSourceSqlSupport.normalizeSql(sql);
        DataSourceSqlSupport.assertAllowedForExternal(connectionResolver.isExternal(dataSourcePath), query);
        List<Object> bindParams = DataSourceSqlSupport.normalizeParams(params);
        int rowLimit = resolveMaxRows(maxRows);

        if (connectionResolver.isExternal(dataSourcePath) && DataSourceSqlSupport.isReadQuery(query)) {
            return dataSourceSqlSession.callForMetadata(dataSourcePath, template ->
                    executeOnTemplate(template, query, bindParams, rowLimit));
        }
        return dataSourceSqlSession.callWithDataSource(dataSourcePath, template ->
                executeOnTemplate(template, query, bindParams, rowLimit));
    }

    static String grammarMessage(BadSqlGrammarException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage().trim();
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage().trim();
        }
        return "Invalid SQL";
    }

    private static DataSourceQueryResult executeOnTemplate(
            JdbcTemplate template,
            String query,
            List<Object> bindParams,
            int rowLimit
    ) {
        if (DataSourceSqlSupport.isReadQuery(query)) {
            ReadResult read = readLimited(template, query, bindParams, rowLimit);
            return DataSourceQueryResult.rows(read.rows(), read.columns(), read.rows().size());
        }
        int updated = template.update(query, bindParams.toArray());
        return DataSourceQueryResult.update(updated);
    }

    /**
     * Stops the driver at {@code rowLimit} via {@code Statement.setMaxRows} so a table preview
     * does not pull the whole table. The shared {@link JdbcTemplate} is not mutated.
     */
    private static ReadResult readLimited(
            JdbcTemplate template,
            String query,
            List<Object> bindParams,
            int rowLimit
    ) {
        ArgumentPreparedStatementSetter setter = new ArgumentPreparedStatementSetter(bindParams.toArray()) {
            @Override
            public void setValues(java.sql.PreparedStatement ps) throws SQLException {
                ps.setMaxRows(rowLimit);
                super.setValues(ps);
            }
        };
        ResultSetExtractor<ReadResult> extractor = rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columnNames = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(meta.getColumnLabel(i));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columnNames.get(i - 1), rs.getObject(i));
                }
                rows.add(normalizeRow(row));
            }
            return new ReadResult(rows, List.copyOf(columnNames));
        };
        ReadResult read = template.query(query, setter, extractor);
        return read != null ? read : new ReadResult(List.of(), List.of());
    }

    private record ReadResult(List<Map<String, Object>> rows, List<String> columns) {
    }

    private static int resolveMaxRows(Integer maxRows) {
        if (maxRows == null || maxRows <= 0) {
            return DEFAULT_MAX_ROWS;
        }
        return Math.min(maxRows, HARD_MAX_ROWS);
    }

    private static Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }
}
