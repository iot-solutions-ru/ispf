package com.ispf.server.query.oq;

public record ObjectQueryJoinOnSpec(
        JoinKind kind,
        String left,
        String right,
        String match,
        String catalogPathPattern
) {
}
