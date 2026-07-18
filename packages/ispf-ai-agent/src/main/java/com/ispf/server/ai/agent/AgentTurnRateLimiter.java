package com.ispf.server.ai.agent;

import com.ispf.server.config.AiProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-user agent turn quotas: concurrent runs and hourly turn budget.
 */
@Component
public class AgentTurnRateLimiter {

    private final int maxConcurrentTurnsPerUser;
    private final int maxTurnsPerHourPerUser;
    private final Map<String, AtomicInteger> concurrentByUser = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> turnTimestampsByUser = new ConcurrentHashMap<>();

    public AgentTurnRateLimiter(AiProperties properties) {
        this.maxConcurrentTurnsPerUser = Math.max(1, properties.getAgentMaxConcurrentTurnsPerUser());
        this.maxTurnsPerHourPerUser = Math.max(1, properties.getAgentMaxTurnsPerHourPerUser());
    }

    public void acquire(String username) {
        String user = normalize(username);
        AtomicInteger concurrent = concurrentByUser.computeIfAbsent(user, ignored -> new AtomicInteger(0));
        if (concurrent.incrementAndGet() > maxConcurrentTurnsPerUser) {
            concurrent.decrementAndGet();
            throw new AgentRateLimitException(
                    "Too many concurrent agent runs (max " + maxConcurrentTurnsPerUser + ")"
            );
        }
        Deque<Instant> history = turnTimestampsByUser.computeIfAbsent(user, ignored -> new ArrayDeque<>());
        Instant cutoff = Instant.now().minusSeconds(3600);
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst().isBefore(cutoff)) {
                history.removeFirst();
            }
            if (history.size() >= maxTurnsPerHourPerUser) {
                concurrent.decrementAndGet();
                throw new AgentRateLimitException(
                        "Agent turn budget exceeded (max " + maxTurnsPerHourPerUser + " per hour)"
                );
            }
            history.addLast(Instant.now());
        }
    }

    public void release(String username) {
        String user = normalize(username);
        AtomicInteger concurrent = concurrentByUser.get(user);
        if (concurrent != null) {
            concurrent.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) {
            return "anonymous";
        }
        return username.trim().toLowerCase();
    }

    public static final class AgentRateLimitException extends RuntimeException {
        public AgentRateLimitException(String message) {
            super(message);
        }
    }
}
