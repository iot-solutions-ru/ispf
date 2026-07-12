package com.ispf.server.query.oq;

import java.util.List;

public record ObjectQuerySpec(
        ObjectQueryFromSpec from,
        List<ObjectQueryJoinSpec> joins,
        List<ObjectQueryFieldSpec> fields,
        List<ObjectQueryOrderSpec> orderBy,
        Integer limit,
        Integer offset,
        String having,
        List<String> groupBy,
        List<ObjectQueryAggregateSpec> aggregates
) {
    public List<ObjectQueryJoinSpec> joinsOrEmpty() {
        return joins != null ? joins : List.of();
    }

    public List<ObjectQueryFieldSpec> fieldsOrEmpty() {
        return fields != null && !fields.isEmpty()
                ? fields
                : List.of(new ObjectQueryFieldSpec(
                        "path",
                        "path",
                        from != null ? from.aliasOrDefault() : "row",
                        null,
                        null,
                        false,
                        null,
                        null
                ));
    }
}
