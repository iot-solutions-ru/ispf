package com.ispf.server.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Append-only event journal storage (PostgreSQL/Timescale or ClickHouse).
 */
public interface EventJournalStore {

    void appendBatch(List<EventJournalRecord> records);

    void appendOne(EventJournalRecord record);

    List<EventJournalRecord> queryRecent(String objectPath, int limit);

    Optional<EventJournalRecord> findLatest(String objectPath, String eventName);

    long countTotal();

    void purgeOlderThan(Instant cutoff);

    /**
     * Deletes matching journal rows in one store statement (not per-id round-trips).
     *
     * @param objectPath blank means every object
     * @return deleted row count, or {@code -1} if the backend only queued a mutation
     */
    long deleteOlderThan(Instant cutoff, String objectPath);

    /** When false, retention is handled by the backend (Timescale policy or ClickHouse TTL). */
    boolean supportsApplicationRetentionPurge();
}
