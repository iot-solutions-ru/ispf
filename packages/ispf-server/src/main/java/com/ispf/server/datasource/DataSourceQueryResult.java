package com.ispf.server.datasource;

import java.util.List;
import java.util.Map;

public record DataSourceQueryResult(
        String kind,
        List<Map<String, Object>> rows,
        List<String> columns,
        int rowCount,
        int updateCount
) {
    public static DataSourceQueryResult rows(List<Map<String, Object>> rows, List<String> columns, int rowCount) {
        return new DataSourceQueryResult("rows", rows, columns, rowCount, 0);
    }

    public static DataSourceQueryResult update(int updateCount) {
        return new DataSourceQueryResult("update", List.of(), List.of(), 0, updateCount);
    }
}
