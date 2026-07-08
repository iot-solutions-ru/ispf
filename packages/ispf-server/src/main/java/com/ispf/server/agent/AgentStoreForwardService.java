package com.ispf.server.agent;

import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.federation.FederationOutboundEventBuffer;
import com.ispf.server.federation.FederationOutboundEventBufferRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Edge agent store-and-forward facade (BL-145).
 * <p>
 * Delegates to {@link FederationOutboundEventBufferRegistry} while exposing a dedicated
 * {@code ispf.agent.store-forward.*} configuration namespace for edge GA.
 */
@Service
public class AgentStoreForwardService {

    private final AgentStoreForwardProperties properties;
    private final FederationOutboundEventBufferRegistry bufferRegistry;
    private final AgentStoreForwardDiskStore diskStore;
    private final Object persistLock = new Object();

    public AgentStoreForwardService(
            AgentStoreForwardProperties properties,
            FederationOutboundEventBufferRegistry bufferRegistry,
            CommercialLicenseProperties licenseProperties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.bufferRegistry = bufferRegistry;
        this.diskStore = new AgentStoreForwardDiskStore(Path.of(licenseProperties.getDataDir()), objectMapper);
    }

    @PostConstruct
    void loadPersistedBuffers() {
        if (!properties.isEnabled() || !properties.isPersistToDisk()) {
            return;
        }
        bufferRegistry.restorePending(diskStore.load());
    }

    @PreDestroy
    void flushOnShutdown() {
        persistPending();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public FederationOutboundEventBuffer.BufferedEvent enqueue(
            UUID agentId,
            String path,
            String variableName,
            Instant occurredAt
    ) {
        if (!properties.isEnabled()) {
            return null;
        }
        FederationOutboundEventBuffer.BufferedEvent event =
                bufferRegistry.enqueue(agentId, path, variableName, occurredAt);
        persistPending();
        return event;
    }

    public List<FederationOutboundEventBuffer.BufferedEvent> drain(UUID agentId) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        List<FederationOutboundEventBuffer.BufferedEvent> drained = bufferRegistry.drain(agentId);
        persistPending();
        return drained;
    }

    public FederationOutboundEventBuffer.Stats stats(UUID agentId) {
        return bufferRegistry.stats(agentId);
    }

    private void persistPending() {
        if (!properties.isEnabled() || !properties.isPersistToDisk()) {
            return;
        }
        synchronized (persistLock) {
            diskStore.save(bufferRegistry.exportPending());
        }
    }
}
