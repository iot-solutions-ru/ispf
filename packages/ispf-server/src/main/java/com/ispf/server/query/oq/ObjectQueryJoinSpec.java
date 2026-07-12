package com.ispf.server.query.oq;

import java.util.Set;

public record ObjectQueryJoinSpec(
        String alias,
        String type,
        String sourcePathPattern,
        Set<String> objectTypes,
        String filter,
        ObjectQueryJoinOnSpec on
) {
}
