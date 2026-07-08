package com.ispf.server.security;

import com.ispf.server.config.IspfSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoginAttemptLimiterTest {

    @Test
    void locksAfterMaxFailedAttempts() {
        IspfSecurityProperties properties = new IspfSecurityProperties();
        properties.getLogin().setMaxFailedAttempts(3);
        properties.getLogin().setLockoutMinutes(15);
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties);

        limiter.recordFailure("alice", "10.0.0.1");
        limiter.recordFailure("alice", "10.0.0.1");
        assertDoesNotThrow(() -> limiter.checkAllowed("alice", "10.0.0.1"));

        limiter.recordFailure("alice", "10.0.0.1");
        assertThrows(
                LoginAttemptLimiter.LoginLockedException.class,
                () -> limiter.checkAllowed("alice", "10.0.0.1")
        );
    }

    @Test
    void successClearsFailures() {
        IspfSecurityProperties properties = new IspfSecurityProperties();
        properties.getLogin().setMaxFailedAttempts(2);
        properties.getLogin().setLockoutMinutes(15);
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties);

        limiter.recordFailure("bob", "10.0.0.2");
        limiter.recordSuccess("bob", "10.0.0.2");
        limiter.recordFailure("bob", "10.0.0.2");
        assertDoesNotThrow(() -> limiter.checkAllowed("bob", "10.0.0.2"));
    }
}
