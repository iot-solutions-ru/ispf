package com.ispf.server.ai.mcp;

import com.ispf.server.ai.agent.AgentRunState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AgentRunState} keyed by MCP transport connection (HTTP actor or stdio connection id).
 */
@Service
@ConditionalOnProperty(prefix = "ispf.mcp", name = "enabled", havingValue = "true")
public class McpConnectionStateStore {

    private static final class Entry {
        final AgentRunState runState = new AgentRunState();
        volatile String agentSessionId;
        volatile Instant lastTouched = Instant.now();
    }

    private final ConcurrentHashMap<String, Entry> byConnectionKey = new ConcurrentHashMap<>();

    public AgentRunState runStateFor(String connectionKey) {
        if (connectionKey == null || connectionKey.isBlank()) {
            return new AgentRunState();
        }
        Entry entry = byConnectionKey.computeIfAbsent(connectionKey.trim(), ignored -> new Entry());
        entry.lastTouched = Instant.now();
        return entry.runState;
    }

    public Optional<String> agentSessionId(String connectionKey) {
        if (connectionKey == null || connectionKey.isBlank()) {
            return Optional.empty();
        }
        Entry entry = byConnectionKey.get(connectionKey.trim());
        if (entry == null || entry.agentSessionId == null || entry.agentSessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(entry.agentSessionId);
    }

    public void bindAgentSession(String connectionKey, String sessionId) {
        if (connectionKey == null || connectionKey.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        Entry entry = byConnectionKey.computeIfAbsent(connectionKey.trim(), ignored -> new Entry());
        entry.agentSessionId = sessionId.trim();
        entry.lastTouched = Instant.now();
    }

    public void clear(String connectionKey) {
        if (connectionKey != null && !connectionKey.isBlank()) {
            byConnectionKey.remove(connectionKey.trim());
        }
    }
}
