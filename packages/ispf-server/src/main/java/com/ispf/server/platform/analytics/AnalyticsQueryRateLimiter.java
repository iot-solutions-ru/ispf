package com.ispf.server.platform.analytics;

import com.ispf.server.config.AnalyticsProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Soft per-tenant rate limit for analytics query API (BL-206).
 */
@Component
public class AnalyticsQueryRateLimiter {

    private static final String DEFAULT_TENANT = "default";

    private final AnalyticsProperties analyticsProperties;
    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public AnalyticsQueryRateLimiter(AnalyticsProperties analyticsProperties) {
        this.analyticsProperties = analyticsProperties;
    }

    public void acquire() {
        int limit = analyticsProperties.queryRateLimitPerMinute();
        if (limit <= 0) {
            return;
        }
        String tenant = DEFAULT_TENANT;
        Deque<Instant> window = windows.computeIfAbsent(tenant, key -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minusSeconds(60);
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                throw new AnalyticsQueryRateLimitException(
                        "Analytics query rate limit exceeded (" + limit + "/minute)"
                );
            }
            window.addLast(Instant.now());
        }
    }

    public static final class AnalyticsQueryRateLimitException extends RuntimeException {
        public AnalyticsQueryRateLimitException(String message) {
            super(message);
        }
    }
}
