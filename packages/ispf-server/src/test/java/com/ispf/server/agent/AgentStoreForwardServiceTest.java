package com.ispf.server.agent;

import com.ispf.server.config.CommercialLicenseProperties;
import com.ispf.server.federation.FederationOutboundEventBufferRegistry;
import com.ispf.server.federation.FederationOutboundBufferProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStoreForwardServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void enqueueAndDrainWhenEnabled() {
        AgentStoreForwardService service = newService(true, true);

        UUID agentId = UUID.randomUUID();
        service.enqueue(agentId, "root.platform.devices.a", "temperature", Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(1, service.stats(agentId).count());
        assertEquals(1, service.drain(agentId).size());
        assertTrue(service.drain(agentId).isEmpty());
    }

    @Test
    void disabledSkipsEnqueue() {
        AgentStoreForwardService service = newService(false, true);

        UUID agentId = UUID.randomUUID();
        assertNull(service.enqueue(agentId, "root.platform.devices.a", "temperature", Instant.now()));
        assertEquals(0, service.stats(agentId).count());
    }

    @Test
    void bufferSurvivesRestartViaDiskPersistence() {
        UUID agentId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T12:00:00Z");

        AgentStoreForwardService first = newService(true, true);
        first.enqueue(agentId, "root.platform.devices.a", "temperature", occurredAt);
        assertEquals(1, first.stats(agentId).count());

        AgentStoreForwardService second = newService(true, true);
        second.loadPersistedBuffers();
        assertEquals(1, second.stats(agentId).count());
        assertEquals("temperature", second.drain(agentId).getFirst().variableName());
    }

    private AgentStoreForwardService newService(boolean enabled, boolean persistToDisk) {
        FederationOutboundBufferProperties bufferProperties = new FederationOutboundBufferProperties();
        FederationOutboundEventBufferRegistry registry = new FederationOutboundEventBufferRegistry(bufferProperties);
        AgentStoreForwardProperties properties = new AgentStoreForwardProperties();
        properties.setEnabled(enabled);
        properties.setPersistToDisk(persistToDisk);
        CommercialLicenseProperties licenseProperties = new CommercialLicenseProperties();
        licenseProperties.setDataDir(tempDir.toString());
        return new AgentStoreForwardService(properties, registry, licenseProperties, new ObjectMapper());
    }
}
