package com.ispf.server.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Retries storage bootstrap while external databases finish starting (empty volume, docker compose).
 */
public final class StorageStartupRetry {

    private static final Logger log = LoggerFactory.getLogger(StorageStartupRetry.class);

    public static final int DEFAULT_ATTEMPTS = 30;
    public static final Duration DEFAULT_DELAY = Duration.ofSeconds(2);

    private StorageStartupRetry() {
    }

    public static void run(String label, Runnable action) {
        run(label, DEFAULT_ATTEMPTS, DEFAULT_DELAY, action);
    }

    public static void run(String label, int maxAttempts, Duration delay, Runnable action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                if (attempt > 1) {
                    log.info("{} ready after {} attempt(s)", label, attempt);
                }
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt >= maxAttempts) {
                    break;
                }
                log.warn(
                        "{} not ready (attempt {}/{}): {}",
                        label,
                        attempt,
                        maxAttempts,
                        ex.getMessage()
                );
                sleepQuietly(delay);
            }
        }
        throw new IllegalStateException(
                label + " schema bootstrap failed after " + maxAttempts + " attempt(s)",
                lastFailure
        );
    }

    private static void sleepQuietly(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Storage bootstrap interrupted", ex);
        }
    }
}
