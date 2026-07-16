package com.ispf.server.event;

import com.ispf.core.object.ObjectEvent;
import com.ispf.server.config.EventJournalProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory ring buffer of recently fired events for fast UI reads while async persistence catches up.
 */
@Component
public class RecentEventCache {

    private final EventJournalProperties properties;
    private final Object lock = new Object();
    private ObjectEvent[] ring = new ObjectEvent[0];
    private int head;
    private int size;

    public RecentEventCache(EventJournalProperties properties) {
        this.properties = properties;
        resizeRing(properties.getRecentCacheSize());
    }

    public boolean isEnabled() {
        return properties.getRecentCacheSize() > 0;
    }

    public void append(ObjectEvent event) {
        if (!isEnabled()) {
            return;
        }
        synchronized (lock) {
            if (ring.length == 0) {
                return;
            }
            ring[head] = event;
            head = (head + 1) % ring.length;
            if (size < ring.length) {
                size++;
            }
        }
    }

    public List<ObjectEvent> query(String objectPath, int limit) {
        if (!isEnabled() || limit <= 0) {
            return List.of();
        }
        synchronized (lock) {
            return collect(limit, objectPath);
        }
    }

    public Optional<ObjectEvent> findLatest(String objectPath, String eventName) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        synchronized (lock) {
            for (int i = 0; i < size; i++) {
                int index = (head - 1 - i + ring.length) % ring.length;
                ObjectEvent event = ring[index];
                if (event == null) {
                    continue;
                }
                if (objectPath.equals(event.objectPath()) && eventName.equals(event.eventName())) {
                    return Optional.of(event);
                }
            }
            return Optional.empty();
        }
    }

    private List<ObjectEvent> collect(int limit, String objectPathFilter) {
        List<ObjectEvent> result = new ArrayList<>(Math.min(limit, size));
        for (int i = 0; i < size && result.size() < limit; i++) {
            int index = (head - 1 - i + ring.length) % ring.length;
            ObjectEvent event = ring[index];
            if (event == null) {
                continue;
            }
            if (objectPathFilter == null
                    || objectPathFilter.isBlank()
                    || objectPathFilter.equals(event.objectPath())
                    || event.objectPath().startsWith(objectPathFilter + ".")) {
                result.add(event);
            }
        }
        return result;
    }

    private void resizeRing(int capacity) {
        int normalized = Math.max(0, capacity);
        ring = normalized == 0 ? new ObjectEvent[0] : new ObjectEvent[normalized];
        head = 0;
        size = 0;
    }
}
