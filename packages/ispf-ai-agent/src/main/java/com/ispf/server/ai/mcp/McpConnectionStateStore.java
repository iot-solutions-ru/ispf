package com.ispf.server.ai.mcp;

import com.ispf.server.ai.agent.AgentRunState;
import com.ispf.server.config.McpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    private final McpProperties properties;
    private final ConcurrentHashMap<String, Entry> byConnectionKey = new ConcurrentHashMap<>();

    public McpConnectionStateStore(McpProperties properties) {
        this.properties = properties;
    }

  /**
     * Mint or resume an HTTP MCP session for {@code actor}. Returns the session id clients send as
     * {@code Mcp-Session-Id}.
     */
    public String initializeHttpSession(String actor, String existingSessionId) {
        evictStaleEntries();
        String sessionId = existingSessionId != null && !existingSessionId.isBlank()
                ? existingSessionId.trim()
                : UUID.randomUUID().toString();
        String connectionKey = McpConnectionKeys.forHttpActor(actor, sessionId);
        byConnectionKey.computeIfAbsent(connectionKey, ignored -> new Entry()).lastTouched = Instant.now();
        return sessionId;
    }

    public boolean hasHttpSession(String actor, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        evictStaleEntries();
        String connectionKey = McpConnectionKeys.forHttpActor(actor, sessionId.trim());
        Entry entry = byConnectionKey.get(connectionKey);
        return entry != null && !isExpired(entry);
    }

    public AgentRunState runStateFor(String connectionKey) {
        if (connectionKey == null || connectionKey.isBlank()) {
            return new AgentRunState();
        }
        evictStaleEntries();
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

    private boolean isExpired(Entry entry) {
        if (entry == null) {
            return true;
        }
        return entry.lastTouched.isBefore(Instant.now().minus(properties.getConnectionIdleTtl()));
    }

    private void evictStaleEntries() {
        Instant cutoff = Instant.now().minus(properties.getConnectionIdleTtl());
        byConnectionKey.entrySet().removeIf(e -> e.getValue().lastTouched.isBefore(cutoff));
        int max = Math.max(16, properties.getMaxConnectionEntries());
        if (byConnectionKey.size() <= max) {
            return;
        }
        int toRemove = byConnectionKey.size() - max;
        byConnectionKey.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().lastTouched))
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(byConnectionKey::remove);
    }
}
