package com.ispf.server.history;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Read path for {@code variable_samples} (JPA/JDBC or ClickHouse depending on store).
 */
public interface VariableHistoryQueryStore {

    List<VariableHistoryService.VariableHistorySample> query(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    );

    List<VariableHistoryService.VariableHistoryBucket> aggregateBuckets(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            Duration bucket,
            int maxBuckets
    );

    /** When false, retention is handled by the backend (Timescale policy or ClickHouse TTL). */
    boolean supportsApplicationRetentionPurge();
}
