package com.ispf.server.object.pubsub;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks WebSocket path subscriptions for demand-driven publication (ADR-0024).
 */
@Component
public class ObjectWebSocketPathInterestRegistry {

    private final AtomicInteger broadcastSessionCount = new AtomicInteger();
    private final ConcurrentHashMap<String, AtomicInteger> pathRefCounts = new ConcurrentHashMap<>();

    public void onBroadcastSessionAdded() {
        broadcastSessionCount.incrementAndGet();
    }

    public void onBroadcastSessionRemoved() {
        broadcastSessionCount.updateAndGet(count -> Math.max(0, count - 1));
    }

    public void subscribePath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        pathRefCounts.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public void unsubscribePath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        pathRefCounts.computeIfPresent(path, (key, count) -> {
            if (count.decrementAndGet() <= 0) {
                return null;
            }
            return count;
        });
    }

    public boolean hasPathInterest(String eventPath) {
        if (eventPath == null || eventPath.isBlank()) {
            return broadcastSessionCount.get() > 0;
        }
        if (broadcastSessionCount.get() > 0) {
            return true;
        }
        if (pathRefCounts.isEmpty()) {
            return false;
        }
        String prefix = "";
        for (String part : eventPath.split("\\.")) {
            prefix = prefix.isEmpty() ? part : prefix + "." + part;
            if (hasExactPath(prefix)) {
                return true;
            }
        }
        String childPrefix = eventPath + ".";
        for (String subscribedPath : pathRefCounts.keySet()) {
            if (subscribedPath.startsWith(childPrefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExactPath(String path) {
        AtomicInteger count = pathRefCounts.get(path);
        return count != null && count.get() > 0;
    }
}
