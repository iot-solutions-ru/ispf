package com.ispf.server.platform.analytics.frames;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index of active event frames (BL-208).
 */
@Component
public class EventFrameRegistry {

    private final EventFrameStore store;
    private final Map<UUID, EventFrame> activeById = new ConcurrentHashMap<>();
    private final Map<String, EventFrame> activeByScopeType = new ConcurrentHashMap<>();

    public EventFrameRegistry(EventFrameStore store) {
        this.store = store;
    }

    @PostConstruct
    void loadActive() {
        for (EventFrame frame : store.loadActiveOnStartup()) {
            registerActive(frame);
        }
    }

    void registerActive(EventFrame frame) {
        activeById.put(frame.frameId(), frame);
        activeByScopeType.put(scopeTypeKey(frame.scopePath(), frame.frameType()), frame);
    }

    void unregister(EventFrame frame) {
        activeById.remove(frame.frameId());
        String key = scopeTypeKey(frame.scopePath(), frame.frameType());
        EventFrame current = activeByScopeType.get(key);
        if (current != null && current.frameId().equals(frame.frameId())) {
            activeByScopeType.remove(key);
        }
    }

    Optional<EventFrame> get(UUID frameId) {
        EventFrame cached = activeById.get(frameId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return store.find(frameId);
    }

    Optional<EventFrame> active(String scopePath, EventFrameType type) {
        EventFrame cached = activeByScopeType.get(scopeTypeKey(scopePath, type));
        if (cached != null) {
            return Optional.of(cached);
        }
        return store.findActive(scopePath, type);
    }

    List<EventFrame> listActive(String scopePath) {
        if (scopePath == null || scopePath.isBlank()) {
            return List.copyOf(activeById.values());
        }
        return activeById.values().stream()
                .filter(frame -> scopePath.equals(frame.scopePath()))
                .toList();
    }

    private static String scopeTypeKey(String scopePath, EventFrameType type) {
        return scopePath + "|" + type.externalName();
    }
}
