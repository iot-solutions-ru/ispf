package com.ispf.server.cluster;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Suppresses NATS {@code slowConsumerDetected} log storms while keeping periodic visibility.
 */
final class RateLimitedNatsErrorListener implements ErrorListener {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedNatsErrorListener.class);

    private final long slowConsumerLogIntervalMs;
    private final AtomicLong lastSlowConsumerLogAt = new AtomicLong(0);

    RateLimitedNatsErrorListener(int slowConsumerLogIntervalSeconds) {
        int seconds = Math.max(1, slowConsumerLogIntervalSeconds);
        this.slowConsumerLogIntervalMs = seconds * 1000L;
    }

    @Override
    public void slowConsumerDetected(Connection conn, Consumer consumer) {
        long now = System.currentTimeMillis();
        long last = lastSlowConsumerLogAt.get();
        if (now - last < slowConsumerLogIntervalMs) {
            return;
        }
        if (!lastSlowConsumerLogAt.compareAndSet(last, now)) {
            return;
        }
        long dropped = consumer != null ? consumer.getDroppedCount() : -1L;
        log.warn("NATS slow consumer detected (droppedMessages={})", dropped);
    }

    @Override
    public void exceptionOccurred(Connection conn, Exception exp) {
        if (exp != null) {
            log.debug("NATS connection exception: {}", exp.getMessage());
        }
    }
}
