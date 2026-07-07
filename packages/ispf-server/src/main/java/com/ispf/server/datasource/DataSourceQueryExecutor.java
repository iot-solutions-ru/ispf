package com.ispf.server.datasource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataSourceQueryExecutor {

    public static final int DEFAULT_MAX_ROWS = 10_000;
    public static final int HARD_MAX_ROWS = 100_000;

    private final DataSourceSqlSession dataSourceSqlSession;

    public DataSourceQueryExecutor(DataSourceSqlSession dataSourceSqlSession) {
        this.dataSourceSqlSession = dataSourceSqlSession;
    }

    public DataSourceQueryResult execute(String dataSourcePath, String sql, List<Object> params, Integer maxRows) {
        String query = DataSourceSqlSupport.normalizeSql(sql);
        List<Object> bindParams = DataSourceSqlSupport.normalizeParams(params);
        int rowLimit = resolveMaxRows(maxRows);

        return dataSourceSqlSession.callWithDataSource(dataSourcePath, template ->
                executeOnTemplate(template, query, bindParams, rowLimit));
    }

    private static DataSourceQueryResult executeOnTemplate(
            JdbcTemplate template,
            String query,
            List<Object> bindParams,
            int rowLimit
    ) {
        if (DataSourceSqlSupport.isReadQuery(query)) {
            List<Map<String, Object>> rows = template.queryForList(query, bindParams.toArray());
            if (rows.size() > rowLimit) {
                rows = new ArrayList<>(rows.subList(0, rowLimit));
            }
            List<Map<String, Object>> normalized = rows.stream()
                    .map(DataSourceQueryExecutor::normalizeRow)
                    .toList();
            List<String> columns = normalized.isEmpty()
                    ? List.of()
                    : List.copyOf(normalized.get(0).keySet());
            return DataSourceQueryResult.rows(normalized, columns, normalized.size());
        }
        int updated = template.update(query, bindParams.toArray());
        return DataSourceQueryResult.update(updated);
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
