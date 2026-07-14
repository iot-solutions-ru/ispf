package com.ispf.server.object.pubsub;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks WebSocket path / variable subscriptions for demand-driven publication (ADR-0024).
 * Empty interest is silence — never "subscribe to the whole server".
 */
@Component
public class ObjectWebSocketPathInterestRegistry {

    /** Path-wide: any variable on (or under) this path. */
    private final ConcurrentHashMap<String, AtomicInteger> pathWideRefCounts = new ConcurrentHashMap<>();
    /** Path → variable → refcount. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>> variableRefCounts =
            new ConcurrentHashMap<>();

    public void subscribePath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        pathWideRefCounts.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public void unsubscribePath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        pathWideRefCounts.computeIfPresent(path, (key, count) -> {
            if (count.decrementAndGet() <= 0) {
                return null;
            }
            return count;
        });
    }

    public void subscribeVariables(String path, Collection<String> variables) {
        if (path == null || path.isBlank() || variables == null || variables.isEmpty()) {
            return;
        }
        ConcurrentHashMap<String, AtomicInteger> byVar =
                variableRefCounts.computeIfAbsent(path, ignored -> new ConcurrentHashMap<>());
        for (String variable : variables) {
            if (variable == null || variable.isBlank() || "*".equals(variable.trim())) {
                continue;
            }
            byVar.computeIfAbsent(variable.trim(), ignored -> new AtomicInteger()).incrementAndGet();
        }
        if (byVar.isEmpty()) {
            variableRefCounts.remove(path, byVar);
        }
    }

    public void unsubscribeVariables(String path, Collection<String> variables) {
        if (path == null || path.isBlank() || variables == null || variables.isEmpty()) {
            return;
        }
        variableRefCounts.computeIfPresent(path, (key, byVar) -> {
            for (String variable : variables) {
                if (variable == null || variable.isBlank()) {
                    continue;
                }
                byVar.computeIfPresent(variable.trim(), (varKey, count) -> {
                    if (count.decrementAndGet() <= 0) {
                        return null;
                    }
                    return count;
                });
            }
            return byVar.isEmpty() ? null : byVar;
        });
    }

    /** True when any UI watch (path-wide or any variable) matches the event path. */
    public boolean hasPathInterest(String eventPath) {
        if (eventPath == null || eventPath.isBlank()) {
            return false;
        }
        return matchesPathWide(eventPath) || matchesAnyVariable(eventPath);
    }

    /** True when UI refresh should publish for this exact variable. */
    public boolean hasVariableInterest(String eventPath, String variableName) {
        if (eventPath == null || eventPath.isBlank() || variableName == null || variableName.isBlank()) {
            return false;
        }
        if (matchesPathWide(eventPath)) {
            return true;
        }
        return matchesVariable(eventPath, variableName.trim());
    }

    /** @deprecated Broadcast mode removed — empty subscribe is silence. */
    @Deprecated
    public void onBroadcastSessionAdded() {
        // no-op
    }

    /** @deprecated Broadcast mode removed — empty subscribe is silence. */
    @Deprecated
    public void onBroadcastSessionRemoved() {
        // no-op
    }

    private boolean matchesPathWide(String eventPath) {
        if (pathWideRefCounts.isEmpty()) {
            return false;
        }
        String prefix = "";
        for (String part : eventPath.split("\\.")) {
            prefix = prefix.isEmpty() ? part : prefix + "." + part;
            if (hasExactPathWide(prefix)) {
                return true;
            }
        }
        String childPrefix = eventPath + ".";
        for (String subscribedPath : pathWideRefCounts.keySet()) {
            if (subscribedPath.startsWith(childPrefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyVariable(String eventPath) {
        if (variableRefCounts.isEmpty()) {
            return false;
        }
        String prefix = "";
        for (String part : eventPath.split("\\.")) {
            prefix = prefix.isEmpty() ? part : prefix + "." + part;
            ConcurrentHashMap<String, AtomicInteger> byVar = variableRefCounts.get(prefix);
            if (byVar != null && !byVar.isEmpty()) {
                return true;
            }
        }
        String childPrefix = eventPath + ".";
        for (String subscribedPath : variableRefCounts.keySet()) {
            if (subscribedPath.startsWith(childPrefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesVariable(String eventPath, String variableName) {
        if (variableRefCounts.isEmpty()) {
            return false;
        }
        String prefix = "";
        for (String part : eventPath.split("\\.")) {
            prefix = prefix.isEmpty() ? part : prefix + "." + part;
            if (hasExactVariable(prefix, variableName)) {
                return true;
            }
        }
        String childPrefix = eventPath + ".";
        for (Map.Entry<String, ConcurrentHashMap<String, AtomicInteger>> entry : variableRefCounts.entrySet()) {
            if (entry.getKey().startsWith(childPrefix) && hasExactVariable(entry.getKey(), variableName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExactPathWide(String path) {
        AtomicInteger count = pathWideRefCounts.get(path);
        return count != null && count.get() > 0;
    }

    private boolean hasExactVariable(String path, String variableName) {
        ConcurrentHashMap<String, AtomicInteger> byVar = variableRefCounts.get(path);
        if (byVar == null) {
            return false;
        }
        AtomicInteger count = byVar.get(variableName);
        return count != null && count.get() > 0;
    }

    /** Test helper. */
    Set<String> pathWideSnapshot() {
        return Set.copyOf(pathWideRefCounts.keySet());
    }
}
