package com.ispf.server.event;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * O(1) estimate of {@code event_history} row count for metrics and load tests.
 * Initialized from DB once at startup; incremented on each journal persist.
 */
@Component
public class EventHistoryRecordCounter {

    private final AtomicLong total = new AtomicLong(-1);

    public void initialize(long dbCount) {
        total.compareAndSet(-1, dbCount);
    }

    public void recordPersisted(long count) {
        if (count <= 0) {
            return;
        }
        long current = total.get();
        if (current < 0) {
            total.set(count);
        } else {
            total.addAndGet(count);
        }
    }

    public long totalRecords() {
        long current = total.get();
        return current < 0 ? 0 : current;
    }

    public boolean isInitialized() {
        return total.get() >= 0;
    }
}
