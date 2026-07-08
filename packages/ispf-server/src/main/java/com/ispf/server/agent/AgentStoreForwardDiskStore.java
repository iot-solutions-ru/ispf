package com.ispf.server.agent;

import tools.jackson.databind.ObjectMapper;
import com.ispf.server.federation.FederationOutboundEventBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JSON file persistence for agent store-forward buffers (BL-145).
 */
final class AgentStoreForwardDiskStore {

    private static final Logger log = LoggerFactory.getLogger(AgentStoreForwardDiskStore.class);
    private static final String FILE_NAME = "agent/store-forward-buffer.json";

    private final Path filePath;
    private final ObjectMapper objectMapper;

    AgentStoreForwardDiskStore(Path dataDir, ObjectMapper objectMapper) {
        this.filePath = dataDir.resolve(FILE_NAME).normalize();
        this.objectMapper = objectMapper;
    }

    Map<UUID, List<FederationOutboundEventBuffer.BufferedEvent>> load() {
        if (!Files.exists(filePath)) {
            return Map.of();
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            if (bytes.length == 0) {
                return Map.of();
            }
            Snapshot snapshot = objectMapper.readValue(bytes, Snapshot.class);
            if (snapshot == null || snapshot.agents() == null || snapshot.agents().isEmpty()) {
                return Map.of();
            }
            Map<UUID, List<FederationOutboundEventBuffer.BufferedEvent>> restored = new LinkedHashMap<>();
            snapshot.agents().forEach((agentIdText, events) -> {
                if (events == null || events.isEmpty()) {
                    return;
                }
                UUID agentId = UUID.fromString(agentIdText);
                List<FederationOutboundEventBuffer.BufferedEvent> buffered = events.stream()
                        .map(StoredEvent::toBufferedEvent)
                        .toList();
                restored.put(agentId, buffered);
            });
            return restored;
        } catch (Exception e) {
            log.warn("Failed to load agent store-forward buffer from {}: {}", filePath, e.getMessage());
            return Map.of();
        }
    }

    void save(Map<UUID, List<FederationOutboundEventBuffer.BufferedEvent>> agents) {
        try {
            Files.createDirectories(filePath.getParent());
            Map<String, List<StoredEvent>> serialized = new LinkedHashMap<>();
            agents.forEach((agentId, events) -> {
                if (events == null || events.isEmpty()) {
                    return;
                }
                serialized.put(
                        agentId.toString(),
                        events.stream().map(StoredEvent::from).toList()
                );
            });
            Snapshot snapshot = new Snapshot(1, serialized);
            Path temp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), snapshot);
            try {
                Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("Failed to persist agent store-forward buffer to {}: {}", filePath, e.getMessage());
        }
    }

    record Snapshot(int version, Map<String, List<StoredEvent>> agents) {}

    record StoredEvent(long seq, String path, String variableName, String occurredAt) {
        static StoredEvent from(FederationOutboundEventBuffer.BufferedEvent event) {
            return new StoredEvent(
                    event.seq(),
                    event.path(),
                    event.variableName(),
                    event.occurredAt().toString()
            );
        }

        FederationOutboundEventBuffer.BufferedEvent toBufferedEvent() {
            return new FederationOutboundEventBuffer.BufferedEvent(
                    seq,
                    path,
                    variableName,
                    Instant.parse(occurredAt)
            );
        }
    }
}
