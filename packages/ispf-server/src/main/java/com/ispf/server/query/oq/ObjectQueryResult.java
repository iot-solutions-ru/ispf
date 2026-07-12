package com.ispf.server.query.oq;

import java.util.List;
import java.util.Map;

public record ObjectQueryResult(
        List<Map<String, Object>> rows,
        int rowCount
) {
    public static ObjectQueryResult empty() {
        return new ObjectQueryResult(List.of(), 0);
    }
}
