package com.ispf.server.agent;

import java.time.Instant;
import java.util.Map;

public record AgentStoreForwardStats(
        boolean enabled,
        boolean persistToDisk,
        int maxBytes,
        String dropPolicy,
        Map<String, AgentStoreForwardAgentStats> agents,
        int totalPending,
        int totalBytes,
        long totalDropped,
        Instant capturedAt
) {
}
