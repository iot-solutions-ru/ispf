package com.ispf.server.event;

import com.ispf.server.history.TimescaleHypertableInitializer;
import com.ispf.server.relational.JdbcUtcTimestamps;
import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConditionalOnProperty(name = "ispf.event-journal.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcEventJournalStore implements EventJournalStore {

    private static final String INSERT_SQL = """
            INSERT INTO event_history (id, object_path, event_name, level, payload_json, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final int PURGE_BATCH_ROWS = 5000;

    private static final RowMapper<EventJournalRecord> ROW_MAPPER = (rs, rowNum) -> new EventJournalRecord(
            rs.getString("id"),
            rs.getString("object_path"),
            rs.getString("event_name"),
            rs.getString("level"),
            rs.getString("payload_json"),
            JdbcUtcTimestamps.read(rs, "occurred_at")
    );

    private final JdbcTemplate jdbcTemplate;
    private final EventHistoryRecordCounter recordCounter;
    private final TimescaleHypertableInitializer timescaleHypertableInitializer;
    private final RelationalDialect relationalDialect;
    private final TransactionTemplate requiresNewTx;

    public JdbcEventJournalStore(
            JdbcTemplate jdbcTemplate,
            EventHistoryRecordCounter recordCounter,
            TimescaleHypertableInitializer timescaleHypertableInitializer,
            RelationalDialect relationalDialect,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.recordCounter = recordCounter;
        this.timescaleHypertableInitializer = timescaleHypertableInitializer;
        this.relationalDialect = relationalDialect;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        this.requiresNewTx = tx;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendBatch(List<EventJournalRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                records,
                records.size(),
                (statement, record) -> bindInsert(statement, record)
        );
        recordCounter.recordPersisted(records.size());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendOne(EventJournalRecord record) {
        jdbcTemplate.update(INSERT_SQL, statement -> bindInsert(statement, record));
        recordCounter.recordPersisted(1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventJournalRecord> queryRecent(String objectPath, int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        String limitSql = relationalDialect.limitClause();
        if (objectPath == null || objectPath.isBlank()) {
            return jdbcTemplate.query(
                    """
                            SELECT id, object_path, event_name, level, payload_json, occurred_at
                            FROM event_history
                            ORDER BY occurred_at DESC, id DESC
                            """ + limitSql,
                    ROW_MAPPER,
                    capped
            );
        }
        return jdbcTemplate.query(
                """
                        SELECT id, object_path, event_name, level, payload_json, occurred_at
                        FROM event_history
                        WHERE object_path = ?
                        ORDER BY occurred_at DESC, id DESC
                        """ + limitSql,
                ROW_MAPPER,
                objectPath,
                capped
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventJournalRecord> queryRecentBefore(
            String objectPath,
            Instant beforeOccurredAt,
            String beforeId,
            int limit
    ) {
        if (beforeOccurredAt == null) {
            return queryRecent(objectPath, limit);
        }
        int capped = Math.max(1, Math.min(limit, 200));
        String tie = beforeId == null ? "" : beforeId;
        if (objectPath == null || objectPath.isBlank()) {
            return jdbcTemplate.query(
                    connection -> {
                        java.sql.PreparedStatement statement = connection.prepareStatement(
                                """
                                        SELECT id, object_path, event_name, level, payload_json, occurred_at
                                        FROM event_history
                                        WHERE occurred_at < ?
                                           OR (occurred_at = ? AND id < ?)
                                        ORDER BY occurred_at DESC, id DESC
                                        """ + relationalDialect.limitClause());
                        JdbcUtcTimestamps.bind(statement, 1, beforeOccurredAt);
                        JdbcUtcTimestamps.bind(statement, 2, beforeOccurredAt);
                        statement.setString(3, tie);
                        statement.setInt(4, capped);
                        return statement;
                    },
                    ROW_MAPPER
            );
        }
        return jdbcTemplate.query(
                connection -> {
                    java.sql.PreparedStatement statement = connection.prepareStatement(
                            """
                                    SELECT id, object_path, event_name, level, payload_json, occurred_at
                                    FROM event_history
                                    WHERE object_path = ?
                                      AND (occurred_at < ? OR (occurred_at = ? AND id < ?))
                                    ORDER BY occurred_at DESC, id DESC
                                    """ + relationalDialect.limitClause());
                    statement.setString(1, objectPath);
                    JdbcUtcTimestamps.bind(statement, 2, beforeOccurredAt);
                    JdbcUtcTimestamps.bind(statement, 3, beforeOccurredAt);
                    statement.setString(4, tie);
                    statement.setInt(5, capped);
                    return statement;
                },
                ROW_MAPPER
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventJournalRecord> findLatest(String objectPath, String eventName) {
        String sql = """
                SELECT id, object_path, event_name, level, payload_json, occurred_at
                FROM event_history
                WHERE object_path = ? AND event_name = ?
                ORDER BY occurred_at DESC
                """ + relationalDialect.limitClause();
        List<EventJournalRecord> rows = jdbcTemplate.query(
                sql,
                ROW_MAPPER,
                objectPath,
                eventName,
                1
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotal() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_history", Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public void purgeOlderThan(Instant cutoff) {
        deleteOlderThan(cutoff, null);
    }

    @Override
    public long deleteOlderThan(Instant cutoff, String objectPath) {
        if (relationalDialect.kind() == RelationalDbKind.MSSQL
                || relationalDialect.kind() == RelationalDbKind.MYSQL
                || relationalDialect.kind() == RelationalDbKind.ORACLE) {
            return deleteOlderThanInBatches(cutoff, objectPath);
        }
        Long deleted = requiresNewTx.execute(status -> {
            long count;
            if (objectPath == null || objectPath.isBlank()) {
                count = jdbcTemplate.update(connection -> {
                    java.sql.PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM event_history WHERE occurred_at < ?");
                    JdbcUtcTimestamps.bind(statement, 1, cutoff);
                    return statement;
                });
            } else {
                String path = objectPath;
                count = jdbcTemplate.update(connection -> {
                    java.sql.PreparedStatement statement = connection.prepareStatement(
                            """
                                    DELETE FROM event_history
                                    WHERE occurred_at < ?
                                      AND (object_path = ? OR object_path LIKE ?)
                                    """);
                    JdbcUtcTimestamps.bind(statement, 1, cutoff);
                    statement.setString(2, path);
                    statement.setString(3, path + ".%");
                    return statement;
                });
            }
            return count;
        });
        long total = deleted != null ? deleted : 0L;
        recordCounter.recordDeleted(total);
        return total;
    }

    /**
     * MSSQL {@code DELETE TOP} / MySQL {@code DELETE … LIMIT} / Oracle {@code ROWID} +
     * {@code FETCH FIRST}. Each batch commits in its own REQUIRES_NEW transaction.
     */
    private long deleteOlderThanInBatches(Instant cutoff, String objectPath) {
        long total = 0;
        int deleted;
        boolean global = objectPath == null || objectPath.isBlank();
        String sql = batchDeleteSql(global);
        do {
            AtomicInteger batch = new AtomicInteger();
            requiresNewTx.executeWithoutResult(status -> batch.set(jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement statement = connection.prepareStatement(sql);
                JdbcUtcTimestamps.bind(statement, 1, cutoff);
                if (!global) {
                    statement.setString(2, objectPath);
                    statement.setString(3, objectPath + ".%");
                }
                return statement;
            })));
            deleted = batch.get();
            total += deleted;
        } while (deleted == PURGE_BATCH_ROWS);
        recordCounter.recordDeleted(total);
        return total;
    }

    private String batchDeleteSql(boolean global) {
        String where = global
                ? "WHERE occurred_at < ?"
                : "WHERE occurred_at < ? AND (object_path = ? OR object_path LIKE ?)";
        if (relationalDialect.kind() == RelationalDbKind.MSSQL) {
            return "DELETE TOP (" + PURGE_BATCH_ROWS + ") FROM event_history " + where;
        }
        if (relationalDialect.kind() == RelationalDbKind.ORACLE) {
            return "DELETE FROM event_history WHERE ROWID IN (SELECT rid FROM ("
                    + "SELECT ROWID AS rid FROM event_history " + where
                    + " FETCH FIRST " + PURGE_BATCH_ROWS + " ROWS ONLY))";
        }
        return "DELETE FROM event_history " + where + " LIMIT " + PURGE_BATCH_ROWS;
    }

    @Override
    public boolean supportsApplicationRetentionPurge() {
        return !timescaleHypertableInitializer.isEventHistoryTimescaleActive();
    }

    private void bindInsert(java.sql.PreparedStatement statement, EventJournalRecord record)
            throws java.sql.SQLException {
        statement.setString(1, record.id());
        statement.setString(2, record.objectPath());
        statement.setString(3, record.eventName());
        statement.setString(4, record.level());
        statement.setString(5, record.payloadJson());
        JdbcUtcTimestamps.bind(statement, 6, record.occurredAt());
    }
}
