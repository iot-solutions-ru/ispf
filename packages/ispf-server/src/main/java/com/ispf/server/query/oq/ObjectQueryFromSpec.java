package com.ispf.server.query.oq;

import java.util.Set;

public record ObjectQueryFromSpec(
        String alias,
        String sourcePathPattern,
        Set<String> objectTypes,
        String filter,
        ObjectQueryExpandSpec expand
) {
    public String aliasOrDefault() {
        return alias != null && !alias.isBlank() ? alias : "row";
    }
}
