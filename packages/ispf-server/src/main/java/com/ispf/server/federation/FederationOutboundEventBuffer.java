package com.ispf.server.federation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory store-and-forward queue for tunnel {@code event_notify} (BL-117).
 */
public final class FederationOutboundEventBuffer {

    public enum DropPolicy {
        DROP_OLDEST,
        DROP_NEWEST
    }

    public record BufferedEvent(long seq, String path, String variableName, Instant occurredAt) {
        int byteSize() {
            int size = path != null ? path.length() : 0;
            if (variableName != null) {
                size += variableName.length();
            }
            return size + 64;
        }
    }

    public record Stats(int count, int bytes, long dropped) {}

    private final int maxBytes;
    private final DropPolicy dropPolicy;
    private final AtomicLong seq = new AtomicLong(0);
    private final Deque<BufferedEvent> events = new ArrayDeque<>();
    private int totalBytes;
    private long dropped;

    public FederationOutboundEventBuffer(int maxBytes, DropPolicy dropPolicy) {
        this.maxBytes = Math.max(256, maxBytes);
        this.dropPolicy = dropPolicy != null ? dropPolicy : DropPolicy.DROP_OLDEST;
    }

    public synchronized BufferedEvent enqueue(String path, String variableName, Instant occurredAt) {
        BufferedEvent event = new BufferedEvent(
                seq.incrementAndGet(),
                path,
                variableName,
                occurredAt != null ? occurredAt : Instant.now()
        );
        int eventBytes = event.byteSize();
        if (dropPolicy == DropPolicy.DROP_NEWEST && totalBytes + eventBytes > maxBytes) {
            dropped++;
            return null;
        }
        while (totalBytes + eventBytes > maxBytes && !events.isEmpty()) {
            BufferedEvent removed = events.removeFirst();
            totalBytes -= removed.byteSize();
            dropped++;
        }
        if (totalBytes + eventBytes > maxBytes) {
            dropped++;
            return null;
        }
        events.addLast(event);
        totalBytes += eventBytes;
        return event;
    }

    public synchronized List<BufferedEvent> drainOrdered() {
        List<BufferedEvent> copy = new ArrayList<>(events);
        events.clear();
        totalBytes = 0;
        return copy;
    }

    public synchronized Stats stats() {
        return new Stats(events.size(), totalBytes, dropped);
    }

    public synchronized boolean isEmpty() {
        return events.isEmpty();
    }
}
