package com.ispf.server.ai.context;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * O(1) invalidation for {@link PlatformBriefingService} without Redis {@code KEYS}.
 */
@Component
public class PlatformBriefingCacheEpoch {

    private final AtomicLong epoch = new AtomicLong();

    public long current() {
        return epoch.get();
    }

    public void bump() {
        epoch.incrementAndGet();
    }
}
