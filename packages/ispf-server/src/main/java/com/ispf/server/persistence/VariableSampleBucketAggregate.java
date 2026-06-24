package com.ispf.server.persistence;

import java.time.Instant;

/**
 * SQL aggregation row for {@link VariableSampleRepository#aggregateBuckets}.
 */
public interface VariableSampleBucketAggregate {

    Instant getBucketStart();

    Double getAvgVal();

    Double getMinVal();

    Double getMaxVal();

    Long getSampleCount();
}
