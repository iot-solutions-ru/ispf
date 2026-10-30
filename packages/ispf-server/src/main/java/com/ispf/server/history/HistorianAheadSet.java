package com.ispf.server.history;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded set of in-flight dual-write keys. Failed secondary/warm writes must not grow
 * without limit when each retry uses a new {@code sampledAt}.
 */
final class HistorianAheadSet {

    static final int DEFAULT_MAX_KEYS = 10_000;

    private final int maxKeys;
    private final ConcurrentHashMap<String, Long> keys = new ConcurrentHashMap<>();

    HistorianAheadSet() {
        this(DEFAULT_MAX_KEYS);
    }

    HistorianAheadSet(int maxKeys) {
        this.maxKeys = Math.max(1, maxKeys);
    }

    boolean contains(String key) {
        return keys.containsKey(key);
    }

    void add(String key) {
        keys.put(key, System.nanoTime());
        evictOverflow();
    }

    void remove(String key) {
        keys.remove(key);
    }

    int size() {
        return keys.size();
    }

    private void evictOverflow() {
        int overflow = keys.size() - maxKeys;
        if (overflow <= 0) {
            return;
        }
        keys.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(overflow)
                .map(Map.Entry::getKey)
                .forEach(keys::remove);
    }
}
