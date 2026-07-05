package com.ispf.server.event;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.ispf.server.cassandra.CassandraClient;
import com.ispf.server.cassandra.CassandraElasticParallelBatches;
import com.ispf.server.cassandra.CassandraTimeSeriesSupport;
import com.ispf.server.config.CassandraStoreProperties;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.storage.StorageStartupRetry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * High-throughput append-only event journal backed by Cassandra or Scylla (CQL).
 */
@Service
@ConditionalOnExpression("'${ispf.event-journal.store:jdbc}' == 'cassandra' || '${ispf.event-journal.store:jdbc}' == 'scylla'")
public class CassandraEventJournalStore implements EventJournalStore {

    private static final Logger log = LoggerFactory.getLogger(CassandraEventJournalStore.class);

    private static final String DEFAULT_TABLE = "event_history";
    private static final String GLOBAL_TABLE = "event_history_global";
    private static final String META_TABLE = "event_journal_meta";
    private static final String META_ROW_ID = "total";
    private static final String GLOBAL_PARTITION_PREFIX = "global:";

    private final EventJournalProperties properties;
    private final EventHistoryRecordCounter recordCounter;
    private final AutomationMetricsRecorder automationMetricsRecorder;
    private final CassandraClient client;
    private final CassandraElasticParallelBatches elasticParallelBatches;
    private final String tableQualified;
    private final String globalTableQualified;
    private final String metaTableQualified;

    private PreparedStatement insertEvent;
    private PreparedStatement insertGlobal;
    private PreparedStatement incrementTotal;
    private PreparedStatement selectByObject;
    private PreparedStatement selectGlobalByMonth;
    private PreparedStatement selectLatestByEventName;
    private PreparedStatement selectTotal;

    public CassandraEventJournalStore(
            EventJournalProperties properties,
            EventHistoryRecordCounter recordCounter,
            AutomationMetricsRecorder automationMetricsRecorder
    ) {
        this.properties = properties;
        this.recordCounter = recordCounter;
        this.automationMetricsRecorder = automationMetricsRecorder;
        CassandraStoreProperties settings = properties.getCassandra();
        this.client = new CassandraClient(settings);
        this.elasticParallelBatches = new CassandraElasticParallelBatches(settings);
        String table = settings.resolveTable(DEFAULT_TABLE);
        this.tableQualified = client.qualifiedTable(table);
        this.globalTableQualified = client.qualifiedTable(GLOBAL_TABLE);
        this.metaTableQualified = client.qualifiedTable(META_TABLE);
    }

    @PostConstruct
    public void ensureSchema() {
        StorageStartupRetry.run("Cassandra event journal", this::ensureSchemaOnce);
    }

    private void ensureSchemaOnce() {
        CassandraStoreProperties settings = properties.getCassandra();
        client.execute(String.format(
                """
                CREATE TABLE IF NOT EXISTS %s (
                    object_path text,
                    occurred_at timestamp,
                    id text,
                    event_name text,
                    level text,
                    payload_json text,
                    PRIMARY KEY ((object_path), occurred_at, id)
                ) WITH CLUSTERING ORDER BY (occurred_at DESC, id ASC)
                """,
                tableQualified
        ));
        client.execute(String.format(
                """
                CREATE TABLE IF NOT EXISTS %s (
                    month_bucket text,
                    occurred_at timestamp,
                    id text,
                    object_path text,
                    event_name text,
                    level text,
                    payload_json text,
                    PRIMARY KEY ((month_bucket), occurred_at, id)
                ) WITH CLUSTERING ORDER BY (occurred_at DESC, id ASC)
                """,
                globalTableQualified
        ));
        dropLegacyMetaTableIfNeeded(settings);
        client.execute(String.format(
                """
                CREATE TABLE IF NOT EXISTS %s (
                    id text PRIMARY KEY,
                    total_count counter
                )
                """,
                metaTableQualified
        ));
        client.execute(
                SimpleStatement.newInstance(
                        "UPDATE " + metaTableQualified + " SET total_count = total_count + 0 WHERE id = ?",
                        META_ROW_ID
                )
        );

        insertEvent = client.prepare(
                "INSERT INTO " + tableQualified
                        + " (object_path, occurred_at, id, event_name, level, payload_json) VALUES (?, ?, ?, ?, ?, ?) USING TTL ?"
        );
        insertGlobal = client.prepare(
                "INSERT INTO " + globalTableQualified
                        + " (month_bucket, occurred_at, id, object_path, event_name, level, payload_json)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?) USING TTL ?"
        );
        incrementTotal = client.prepare(
                "UPDATE " + metaTableQualified + " SET total_count = total_count + ? WHERE id = ?"
        );
        selectByObject = client.prepare(
                "SELECT id, object_path, event_name, level, payload_json, occurred_at FROM "
                        + tableQualified + " WHERE object_path = ? LIMIT ?"
        );
        selectGlobalByMonth = client.prepare(
                "SELECT id, object_path, event_name, level, payload_json, occurred_at FROM "
                        + globalTableQualified + " WHERE month_bucket = ? LIMIT ?"
        );
        selectLatestByEventName = client.prepare(
                "SELECT id, object_path, event_name, level, payload_json, occurred_at FROM "
                        + tableQualified
                        + " WHERE object_path = ? AND event_name = ? LIMIT 1 ALLOW FILTERING"
        );
        selectTotal = client.prepare(
                "SELECT total_count FROM " + metaTableQualified + " WHERE id = ?"
        );

        log.info(
                "Cassandra event journal ready (keyspace={}, table={}, retentionDays={}, "
                        + "partitionBatch={}, parallelBatches={}-{}, elasticParallel={}, globalTable={}, asyncCounter={})",
                settings.getKeyspace(),
                settings.resolveTable(DEFAULT_TABLE),
                properties.getRetentionDays(),
                settings.getMaxStatementsPerPartitionBatch(),
                settings.resolvedMinParallelPartitionBatches(),
                settings.getMaxParallelPartitionBatches(),
                settings.isElasticParallelBatchesEnabled(),
                properties.isCassandraGlobalTableEnabled(),
                properties.isCassandraAsyncCounterUpdate()
        );
    }

    @Override
    public void appendBatch(List<EventJournalRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        CassandraStoreProperties settings = properties.getCassandra();
        int ttl = CassandraTimeSeriesSupport.ttlSeconds(properties.getRetentionDays());
        int chunkSize = settings.getMaxStatementsPerPartitionBatch();
        Map<String, List<BoundStatement>> byPartition = new LinkedHashMap<>();
        for (EventJournalRecord record : records) {
            byPartition.computeIfAbsent(record.objectPath(), ignored -> new ArrayList<>())
                    .add(bindInsert(insertEvent, record, ttl));
            if (properties.isCassandraGlobalTableEnabled()) {
                String globalPartition = GLOBAL_PARTITION_PREFIX
                        + CassandraTimeSeriesSupport.monthBucket(record.occurredAt());
                byPartition.computeIfAbsent(globalPartition, ignored -> new ArrayList<>())
                        .add(bindGlobalInsert(record, ttl));
            }
        }
        List<List<BoundStatement>> batches = new ArrayList<>();
        for (List<BoundStatement> partitionStatements : byPartition.values()) {
            batches.addAll(CassandraTimeSeriesSupport.chunk(partitionStatements, chunkSize));
        }
        int maxParallel = elasticParallelBatches.resolve(
                settings,
                automationMetricsRecorder.eventJournalQueueSize(),
                batches.size()
        );
        client.executePartitionBatches(batches, maxParallel);
        var counterUpdate = incrementTotal.bind((long) records.size(), META_ROW_ID);
        if (properties.isCassandraAsyncCounterUpdate()) {
            client.executeAsync(counterUpdate);
        } else {
            client.execute(counterUpdate);
        }
        recordCounter.recordPersisted(records.size());
    }

    @Override
    public void appendOne(EventJournalRecord record) {
        appendBatch(List.of(record));
    }

    @Override
    public List<EventJournalRecord> queryRecent(String objectPath, int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        if (objectPath == null || objectPath.isBlank()) {
            return queryRecentGlobal(capped);
        }
        return client.queryRows(selectByObject.bind(objectPath, capped)).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public Optional<EventJournalRecord> findLatest(String objectPath, String eventName) {
        List<Row> rows = client.queryRows(selectLatestByEventName.bind(objectPath, eventName));
        return rows.isEmpty() ? Optional.empty() : Optional.of(toRecord(rows.getFirst()));
    }

    @Override
    public long countTotal() {
        List<Row> rows = client.queryRows(selectTotal.bind(META_ROW_ID));
        if (rows.isEmpty()) {
            return 0L;
        }
        Long total = rows.getFirst().getLong("total_count");
        return total != null ? total : 0L;
    }

    @Override
    public void purgeOlderThan(Instant cutoff) {
        // Retention handled by per-row TTL.
    }

    @Override
    public boolean supportsApplicationRetentionPurge() {
        return false;
    }

    @PreDestroy
    void closeClient() {
        client.close();
    }

    private void dropLegacyMetaTableIfNeeded(CassandraStoreProperties settings) {
        List<Row> rows = client.queryRows(
                "SELECT type FROM system_schema.columns WHERE keyspace_name = ? AND table_name = ? AND column_name = 'total_count'",
                settings.getKeyspace(),
                META_TABLE
        );
        if (rows.isEmpty()) {
            return;
        }
        String type = rows.getFirst().getString("type");
        if (type != null && !"counter".equals(type)) {
            log.warn(
                    "Dropping legacy {} (total_count type={}, expected counter)",
                    metaTableQualified,
                    type
            );
            client.execute("DROP TABLE IF EXISTS " + metaTableQualified);
        }
    }

    private List<EventJournalRecord> queryRecentGlobal(int limit) {
        List<EventJournalRecord> merged = new ArrayList<>();
        for (String monthBucket : CassandraTimeSeriesSupport.recentMonthBuckets(Instant.now(), 3)) {
            merged.addAll(client.queryRows(selectGlobalByMonth.bind(monthBucket, limit)).stream()
                    .map(this::toRecord)
                    .toList());
            if (merged.size() >= limit) {
                break;
            }
        }
        merged.sort(Comparator.comparing(EventJournalRecord::occurredAt).reversed());
        if (merged.size() > limit) {
            return merged.subList(0, limit);
        }
        return merged;
    }

    private BoundStatement bindInsert(PreparedStatement statement, EventJournalRecord record, int ttlSeconds) {
        return statement.bind(
                record.objectPath(),
                record.occurredAt(),
                record.id(),
                record.eventName(),
                record.level(),
                record.payloadJson(),
                effectiveTtl(ttlSeconds)
        );
    }

    private BoundStatement bindGlobalInsert(EventJournalRecord record, int ttlSeconds) {
        return insertGlobal.bind(
                CassandraTimeSeriesSupport.monthBucket(record.occurredAt()),
                record.occurredAt(),
                record.id(),
                record.objectPath(),
                record.eventName(),
                record.level(),
                record.payloadJson(),
                effectiveTtl(ttlSeconds)
        );
    }

    private static int effectiveTtl(int ttlSeconds) {
        return ttlSeconds > 0 ? ttlSeconds : 0;
    }

    private EventJournalRecord toRecord(Row row) {
        return new EventJournalRecord(
                row.getString("id"),
                row.getString("object_path"),
                row.getString("event_name"),
                row.getString("level"),
                row.getString("payload_json"),
                row.getInstant("occurred_at")
        );
    }
}
