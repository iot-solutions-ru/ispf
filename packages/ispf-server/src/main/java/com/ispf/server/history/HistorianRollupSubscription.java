package com.ispf.server.history;

import java.time.Duration;
import java.util.Objects;

/**
 * One materialized rollup series (BL-205).
 */
public record HistorianRollupSubscription(
        String objectPath,
        String variableName,
        String fieldName,
        Duration bucket
) {

    public HistorianRollupSubscription {
        Objects.requireNonNull(objectPath, "objectPath");
        Objects.requireNonNull(variableName, "variableName");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(bucket, "bucket");
    }

    public String bucketSpec() {
        return HistorianRollupBuckets.formatBucket(bucket);
    }

    public long bucketWidthSec() {
        return bucket.getSeconds();
    }
}
