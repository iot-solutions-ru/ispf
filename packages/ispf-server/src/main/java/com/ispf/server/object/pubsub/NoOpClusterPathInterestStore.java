package com.ispf.server.object.pubsub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ispf.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpClusterPathInterestStore implements ClusterPathInterestStore {
    @Override
    public void onBroadcastSessionAdded() {
    }

    @Override
    public void onBroadcastSessionRemoved() {
    }

    @Override
    public void subscribePath(String path) {
    }

    @Override
    public void unsubscribePath(String path) {
    }

    @Override
    public boolean hasPathInterest(String eventPath) {
        return false;
    }
}
