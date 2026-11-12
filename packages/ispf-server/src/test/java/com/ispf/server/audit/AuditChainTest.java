package com.ispf.server.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditChainTest {

    @Test
    void editedRowBreaksTheChain() {
        Instant at = Instant.parse("2026-11-12T10:00:00Z");
        String first = AuditChain.hash(null, "a", "object", "update", "op", "var", "path", "{\"n\":1}", at);
        String second = AuditChain.hash(first, "b", "object", "update", "op", "var", "path", "{\"n\":2}", at);
        assertTrue(AuditChain.intact(List.of(
                link("a", "{\"n\":1}", null, first, at),
                link("b", "{\"n\":2}", first, second, at)
        )));
        assertFalse(AuditChain.intact(List.of(
                link("a", "{\"n\":9}", null, first, at),
                link("b", "{\"n\":2}", first, second, at)
        )));
    }

    private static AuditChain.Link link(String id, String details, String previous, String chain, Instant at) {
        return new AuditChain.Link(id, "object", "update", "op", "var", "path", details, at, previous, chain);
    }
}
