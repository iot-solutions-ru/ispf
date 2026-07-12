package com.ispf.server.query.oq;

public record ObjectQueryExpandSpec(
        String variable,
        String rowKey,
        String filter
) {
}
