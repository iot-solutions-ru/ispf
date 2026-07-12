package com.ispf.server.query.oq;

public record ObjectQueryFieldSpec(
        String name,
        String source,
        String alias,
        String ref,
        String expression,
        boolean writable,
        String historianFn,
        String historianWindow
) {
}
