package com.ispf.server.agent;

public record AgentStoreForwardAgentStats(
        int pendingCount,
        int pendingBytes,
        long dropped
) {
}
