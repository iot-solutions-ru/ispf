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

    /**
     * Keyset page older than {@code (beforeOccurredAt, beforeId)}.
     * Default filters a larger recent page (stores that only support OFFSET-like reads).
     */
    default List<EventJournalRecord> queryRecentBefore(
            String objectPath,
            Instant beforeOccurredAt,
            String beforeId,
            int limit
    ) {
        if (beforeOccurredAt == null) {
            return queryRecent(objectPath, limit);
        }
        int fetch = Math.min(Math.max(limit * 8, limit), 2000);
        String tieBreak = beforeId == null ? "" : beforeId;
        return queryRecent(objectPath, fetch).stream()
                .filter(record -> {
                    int cmp = record.occurredAt().compareTo(beforeOccurredAt);
                    if (cmp < 0) {
                        return true;
                    }
                    if (cmp > 0) {
                        return false;
                    }
                    return record.id().compareTo(tieBreak) < 0;
                })
                .limit(Math.max(1, limit))
                .toList();
    }

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
