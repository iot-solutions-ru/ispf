package com.ispf.server.security;

import com.ispf.server.config.IspfSecurityProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failed login attempts per username and client IP; blocks brute-force retries.
 */
@Component
public class LoginAttemptLimiter {

    private final int maxFailedAttempts;
    private final int lockoutMinutes;
    private final Map<String, Deque<Instant>> failuresByKey = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(IspfSecurityProperties properties) {
        this.maxFailedAttempts = Math.max(1, properties.getLogin().getMaxFailedAttempts());
        this.lockoutMinutes = Math.max(1, properties.getLogin().getLockoutMinutes());
    }

    public void checkAllowed(String username, String clientIp) {
        if (isLocked(key(username, clientIp))) {
            throw new LoginLockedException(
                    "Too many failed login attempts. Try again in " + lockoutMinutes + " minutes."
            );
        }
    }

    public void recordFailure(String username, String clientIp) {
        String key = key(username, clientIp);
        Deque<Instant> history = failuresByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        Instant cutoff = Instant.now().minusSeconds(lockoutMinutes * 60L);
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst().isBefore(cutoff)) {
                history.removeFirst();
            }
            history.addLast(Instant.now());
        }
    }

    public void recordSuccess(String username, String clientIp) {
        failuresByKey.remove(key(username, clientIp));
    }

    private boolean isLocked(String key) {
        Deque<Instant> history = failuresByKey.get(key);
        if (history == null) {
            return false;
        }
        Instant cutoff = Instant.now().minusSeconds(lockoutMinutes * 60L);
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst().isBefore(cutoff)) {
                history.removeFirst();
            }
            return history.size() >= maxFailedAttempts;
        }
    }

    private static String key(String username, String clientIp) {
        String user = username == null ? "" : username.trim().toLowerCase();
        String ip = clientIp == null ? "" : clientIp.trim();
        return user + "|" + ip;
    }

    public static final class LoginLockedException extends RuntimeException {
        public LoginLockedException(String message) {
            super(message);
        }
    }
}
