package com.ispf.server.federation;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FederationOutboundEventBufferRegistry {

    private final FederationOutboundBufferProperties properties;
    private final Map<UUID, FederationOutboundEventBuffer> buffers = new ConcurrentHashMap<>();

    public FederationOutboundEventBufferRegistry(FederationOutboundBufferProperties properties) {
        this.properties = properties;
    }

    public FederationOutboundEventBuffer.BufferedEvent enqueue(
            UUID agentId,
            String path,
            String variableName,
            Instant occurredAt
    ) {
        return buffer(agentId).enqueue(path, variableName, occurredAt);
    }

    public List<FederationOutboundEventBuffer.BufferedEvent> drain(UUID agentId) {
        return buffer(agentId).drainOrdered();
    }

    public FederationOutboundEventBuffer.Stats stats(UUID agentId) {
        return buffer(agentId).stats();
    }

    public int pendingCount(UUID agentId) {
        return buffer(agentId).stats().count();
    }

    public Map<UUID, FederationOutboundEventBuffer.Stats> allStats() {
        Map<UUID, FederationOutboundEventBuffer.Stats> stats = new LinkedHashMap<>();
        for (Map.Entry<UUID, FederationOutboundEventBuffer> entry : buffers.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().stats());
        }
        return stats;
    }

    public Map<UUID, List<FederationOutboundEventBuffer.BufferedEvent>> exportPending() {
        Map<UUID, List<FederationOutboundEventBuffer.BufferedEvent>> pending = new LinkedHashMap<>();
        for (Map.Entry<UUID, FederationOutboundEventBuffer> entry : buffers.entrySet()) {
            List<FederationOutboundEventBuffer.BufferedEvent> events = entry.getValue().pendingSnapshot();
            if (!events.isEmpty()) {
                pending.put(entry.getKey(), events);
            }
        }
        return pending;
    }

    public void restorePending(Map<UUID, List<FederationOutboundEventBuffer.BufferedEvent>> restored) {
        if (restored == null || restored.isEmpty()) {
            return;
        }
        restored.forEach((agentId, events) -> buffer(agentId).restore(events));
    }

    private FederationOutboundEventBuffer buffer(UUID agentId) {
        return buffers.computeIfAbsent(
                agentId,
                ignored -> new FederationOutboundEventBuffer(
                        properties.maxBytes(),
                        properties.resolvedDropPolicy()
                )
        );
    }
}
