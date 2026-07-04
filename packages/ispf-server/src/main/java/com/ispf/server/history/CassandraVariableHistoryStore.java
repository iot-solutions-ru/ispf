package com.ispf.server.history;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.ispf.server.cassandra.CassandraClient;
import com.ispf.server.cassandra.CassandraTimeSeriesSupport;
import com.ispf.server.config.CassandraStoreProperties;
import com.ispf.server.config.VariableHistoryProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-throughput variable historian backed by Cassandra or Scylla (CQL).
 */
@Service
@ConditionalOnExpression("'${ispf.variable-history.store:jdbc}' == 'cassandra' || '${ispf.variable-history.store:jdbc}' == 'scylla'")
public class CassandraVariableHistoryStore implements VariableHistoryWriteStore, VariableHistoryQueryStore {

    private static final Logger log = LoggerFactory.getLogger(CassandraVariableHistoryStore.class);
    private static final int MAX_AGGREGATE_SAMPLE_ROWS = 100_000;
    private static final String DEFAULT_TABLE = "variable_samples";

    private final VariableHistoryProperties properties;
    private final CassandraClient client;
    private final String tableQualified;

    private PreparedStatement insertSample;
    private PreparedStatement selectRange;
    private PreparedStatement selectRecent;

    public CassandraVariableHistoryStore(VariableHistoryProperties properties) {
        this.properties = properties;
        CassandraStoreProperties settings = properties.getCassandra();
        this.client = new CassandraClient(settings);
        this.tableQualified = client.qualifiedTable(settings.resolveTable(DEFAULT_TABLE));
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(113)
    public void ensureSchema() {
        CassandraStoreProperties settings = properties.getCassandra();
        client.execute(String.format(
                """
                CREATE KEYSPACE IF NOT EXISTS %s
                WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
                """,
                settings.getKeyspace()
        ));
        client.execute(String.format(
                """
                CREATE TABLE IF NOT EXISTS %s (
                    object_path text,
                    variable_name text,
                    field_name text,
                    sampled_at timestamp,
                    observed_at timestamp,
                    value_double double,
                    value_text text,
                    PRIMARY KEY ((object_path, variable_name, field_name), sampled_at)
                ) WITH CLUSTERING ORDER BY (sampled_at ASC)
                """,
                tableQualified
        ));

        insertSample = client.prepare(
                "INSERT INTO " + tableQualified
                        + " (object_path, variable_name, field_name, sampled_at, observed_at, value_double, value_text)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?) USING TTL ?"
        );
        selectRange = client.prepare(
                "SELECT sampled_at, observed_at, value_double, value_text FROM " + tableQualified
                        + " WHERE object_path = ? AND variable_name = ? AND field_name = ?"
                        + " AND sampled_at >= ? AND sampled_at <= ?"
        );
        selectRecent = client.prepare(
                "SELECT sampled_at, observed_at, value_double, value_text FROM " + tableQualified
                        + " WHERE object_path = ? AND variable_name = ? AND field_name = ?"
                        + " ORDER BY sampled_at DESC LIMIT ?"
        );

        log.info(
                "Cassandra variable history ready (keyspace={}, table={}, retentionDays={}, "
                        + "partitionBatch={}, parallelBatches={})",
                settings.getKeyspace(),
                settings.resolveTable(DEFAULT_TABLE),
                properties.getRetentionDays(),
                settings.getMaxStatementsPerPartitionBatch(),
                settings.getMaxParallelPartitionBatches()
        );
    }

    @Override
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        CassandraStoreProperties settings = properties.getCassandra();
        int ttl = CassandraTimeSeriesSupport.ttlSeconds(properties.getRetentionDays());
        int chunkSize = settings.getMaxStatementsPerPartitionBatch();
        int maxParallel = settings.getMaxParallelPartitionBatches();
        Map<String, List<BoundStatement>> byPartition = new LinkedHashMap<>();
        for (VariableHistoryWriteRecord record : records) {
            String partitionKey = CassandraTimeSeriesSupport.variableSamplePartitionKey(
                    record.objectPath(),
                    record.variableName(),
                    record.fieldName()
            );
            byPartition.computeIfAbsent(partitionKey, ignored -> new ArrayList<>())
                    .add(bindInsert(record, ttl));
        }
        List<List<BoundStatement>> batches = new ArrayList<>();
        for (List<BoundStatement> partitionStatements : byPartition.values()) {
            batches.addAll(CassandraTimeSeriesSupport.chunk(partitionStatements, chunkSize));
        }
        client.executePartitionBatches(batches, maxParallel);
    }

    private BoundStatement bindInsert(VariableHistoryWriteRecord record, int ttl) {
        return insertSample.bind(
                record.objectPath(),
                record.variableName(),
                record.fieldName(),
                record.sampledAt(),
                record.observedAt(),
                record.valueDouble(),
                record.valueText(),
                ttl > 0 ? ttl : 0
        );
    }

    @Override
    public void appendOne(VariableHistoryWriteRecord record) {
        appendBatch(List.of(record));
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
        if (from != null && to != null) {
            List<VariableHistoryService.VariableHistorySample> rows = client
                    .queryRows(
                            selectRange.bind(
                                    objectPath,
                                    variableName,
                                    fieldName,
                                    from,
                                    to
                            )
                    )
                    .stream()
                    .map(this::toSample)
                    .sorted(Comparator.comparing(VariableHistoryService.VariableHistorySample::ts))
                    .toList();
            if (rows.size() > cappedLimit) {
                return rows.subList(rows.size() - cappedLimit, rows.size());
            }
            return rows;
        }

        List<VariableHistoryService.VariableHistorySample> rows = client
                .queryRows(selectRecent.bind(objectPath, variableName, fieldName, cappedLimit))
                .stream()
                .map(this::toSample)
                .toList();
        List<VariableHistoryService.VariableHistorySample> reversed = new ArrayList<>(rows);
        Collections.reverse(reversed);
        return reversed;
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
        List<VariableHistoryService.VariableHistorySample> rows = query(
                objectPath,
                variableName,
                fieldName,
                from,
                to,
                MAX_AGGREGATE_SAMPLE_ROWS
        );

        Map<Instant, BucketAccumulator> buckets = new LinkedHashMap<>();
        for (VariableHistoryService.VariableHistorySample row : rows) {
            Double value = row.value();
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            Instant bucketStart = truncateToBucket(row.ts(), bucket);
            buckets.computeIfAbsent(bucketStart, ignored -> new BucketAccumulator()).add(value);
        }

        List<VariableHistoryService.VariableHistoryBucket> result = buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().toBucket(entry.getKey()))
                .toList();

        if (result.size() > maxBuckets) {
            return result.subList(result.size() - maxBuckets, result.size());
        }
        return result;
    }

    @Override
    public boolean supportsApplicationRetentionPurge() {
        return false;
    }

    @PreDestroy
    void closeClient() {
        client.close();
    }

    private VariableHistoryService.VariableHistorySample toSample(Row row) {
        Instant sampledAt = row.getInstant("sampled_at");
        Instant observedAt = row.isNull("observed_at") ? sampledAt : row.getInstant("observed_at");
        Instant effective = observedAt != null ? observedAt : sampledAt;
        return new VariableHistoryService.VariableHistorySample(
                effective,
                row.isNull("value_double") ? null : row.getDouble("value_double"),
                row.getString("value_text"),
                sampledAt
        );
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
