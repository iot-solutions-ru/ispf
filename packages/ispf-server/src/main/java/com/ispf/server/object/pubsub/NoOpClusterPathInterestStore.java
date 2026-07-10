package com.ispf.server.object.pubsub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "!${ispf.redis.enabled:false} or !${ispf.cluster.cluster-path-interest-enabled:true}"
)
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
