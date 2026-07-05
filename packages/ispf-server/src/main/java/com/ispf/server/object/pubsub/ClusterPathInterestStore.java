package com.ispf.server.object.pubsub;

/**
 * ADR-0029: cluster-wide WebSocket path interest (Redis-backed when enabled).
 */
public interface ClusterPathInterestStore {

    void onBroadcastSessionAdded();

    void onBroadcastSessionRemoved();

    void subscribePath(String path);

    void unsubscribePath(String path);

    boolean hasPathInterest(String eventPath);
}
