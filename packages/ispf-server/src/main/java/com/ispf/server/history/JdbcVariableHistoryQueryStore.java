package com.ispf.server.history;

import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.persistence.entity.VariableSampleEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnExpression("'${ispf.variable-history.store:jdbc}' != 'clickhouse'")
class JdbcVariableHistoryQueryStore implements VariableHistoryQueryStore {

    private static final int MAX_AGGREGATE_SAMPLE_ROWS = 100_000;

    private final VariableSampleRepository sampleRepository;
    private final TimescaleHypertableInitializer timescaleHypertableInitializer;

    JdbcVariableHistoryQueryStore(
            VariableSampleRepository sampleRepository,
            TimescaleHypertableInitializer timescaleHypertableInitializer
    ) {
        this.sampleRepository = sampleRepository;
        this.timescaleHypertableInitializer = timescaleHypertableInitializer;
    }

    @Override
    public List<VariableHistoryService.VariableHistorySample> query(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    ) {
        int cappedLimit = Math.min(Math.max(limit, 1), 10_000);

        List<VariableSampleEntity> rows;
        if (from != null && to != null) {
            rows = sampleRepository.findByObjectPathAndVariableNameAndFieldNameAndSampledAtBetweenOrderBySampledAtAsc(
                    objectPath,
                    variableName,
                    fieldName,
                    from,
                    to
            );
            if (rows.size() > cappedLimit) {
                rows = rows.subList(rows.size() - cappedLimit, rows.size());
            }
        } else {
            rows = new ArrayList<>(sampleRepository.findByObjectPathAndVariableNameAndFieldNameOrderBySampledAtDesc(
                    objectPath,
                    variableName,
                    fieldName,
                    PageRequest.of(0, cappedLimit)
            ));
            rows = rows.reversed();
        }

        return rows.stream()
                .map(row -> new VariableHistoryService.VariableHistorySample(
                        row.getSampledAt(),
                        row.getValueDouble(),
                        row.getValueText()
                ))
                .toList();
    }

    @Override
    public List<VariableHistoryService.VariableHistoryBucket> aggregateBuckets(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            Duration bucket,
            int maxBuckets
    ) {
        if (timescaleHypertableInitializer.isPostgreSql()) {
            return aggregateWithSql(objectPath, variableName, fieldName, from, to, bucket, maxBuckets);
        }
        return aggregateWithJvm(objectPath, variableName, fieldName, from, to, bucket, maxBuckets);
    }

    @Override
    public boolean supportsApplicationRetentionPurge() {
        return !timescaleHypertableInitializer.isVariableSamplesTimescaleActive();
    }

    private List<VariableHistoryService.VariableHistoryBucket> aggregateWithSql(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            Duration bucket,
            int maxBuckets
    ) {
        long bucketSeconds = bucket.getSeconds();
        return sampleRepository.aggregateBuckets(
                        objectPath,
                        variableName,
                        fieldName,
                        from,
                        to,
                        bucketSeconds,
                        maxBuckets
                ).stream()
                .map(row -> new VariableHistoryService.VariableHistoryBucket(
                        row.getBucketStart(),
                        row.getAvgVal(),
                        row.getMinVal(),
                        row.getMaxVal(),
                        row.getSampleCount() != null ? row.getSampleCount().intValue() : 0
                ))
                .toList();
    }

    private List<VariableHistoryService.VariableHistoryBucket> aggregateWithJvm(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            Duration bucket,
            int maxBuckets
    ) {
        List<VariableSampleEntity> rows = sampleRepository
                .findByObjectPathAndVariableNameAndFieldNameAndSampledAtBetweenOrderBySampledAtAsc(
                        objectPath,
                        variableName,
                        fieldName,
                        from,
                        to,
                        PageRequest.of(0, MAX_AGGREGATE_SAMPLE_ROWS, Sort.by("sampledAt").ascending())
                );

        Map<Instant, BucketAccumulator> buckets = new LinkedHashMap<>();
        for (VariableSampleEntity row : rows) {
            Double value = row.getValueDouble();
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            Instant bucketStart = truncateToBucket(row.getSampledAt(), bucket);
            buckets.computeIfAbsent(bucketStart, ignored -> new BucketAccumulator()).add(value);
        }

        List<VariableHistoryService.VariableHistoryBucket> result = buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().toBucket(entry.getKey()))
                .toList();

        if (result.size() > maxBuckets) {
            result = result.subList(result.size() - maxBuckets, result.size());
        }
        return result;
    }

    private static Instant truncateToBucket(Instant instant, Duration bucket) {
        long bucketSeconds = bucket.getSeconds();
        if (bucketSeconds <= 0) {
            return instant;
        }
        long floored = Math.floorDiv(instant.getEpochSecond(), bucketSeconds) * bucketSeconds;
        return Instant.ofEpochSecond(floored);
    }

    private static final class BucketAccumulator {
        private double sum;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;
        private int count;

        void add(double value) {
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
            count++;
        }

        VariableHistoryService.VariableHistoryBucket toBucket(Instant ts) {
            return new VariableHistoryService.VariableHistoryBucket(ts, sum / count, min, max, count);
        }
    }
}
