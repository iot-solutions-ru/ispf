package com.ispf.server.relational.store;

import com.ispf.server.persistence.VariableSampleBucketAggregate;

import java.time.Instant;

record SimpleVariableSampleBucketAggregate(
        Instant bucketStart,
        Double avgVal,
        Double minVal,
        Double maxVal,
        Long sampleCount
) implements VariableSampleBucketAggregate {

    @Override
    public Instant getBucketStart() {
        return bucketStart;
    }

    @Override
    public Double getAvgVal() {
        return avgVal;
    }

    @Override
    public Double getMinVal() {
        return minVal;
    }

    @Override
    public Double getMaxVal() {
        return maxVal;
    }

    @Override
    public Long getSampleCount() {
        return sampleCount;
    }
}
