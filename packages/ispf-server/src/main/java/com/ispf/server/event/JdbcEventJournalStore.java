package com.ispf.server.event;

import com.ispf.server.history.TimescaleHypertableInitializer;
import com.ispf.server.relational.JdbcUtcTimestamps;
import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "ispf.event-journal.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcEventJournalStore implements EventJournalStore {

    private static final String INSERT_SQL = """
            INSERT INTO event_history (id, object_path, event_name, level, payload_json, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

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

    public JdbcEventJournalStore(
            JdbcTemplate jdbcTemplate,
            EventHistoryRecordCounter recordCounter,
            TimescaleHypertableInitializer timescaleHypertableInitializer,
            RelationalDialect relationalDialect
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.recordCounter = recordCounter;
        this.timescaleHypertableInitializer = timescaleHypertableInitializer;
        this.relationalDialect = relationalDialect;
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeOlderThan(Instant cutoff) {
        deleteOlderThan(cutoff, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long deleteOlderThan(Instant cutoff, String objectPath) {
        if (relationalDialect.kind() == RelationalDbKind.MSSQL) {
            return deleteOlderThanMssql(cutoff, objectPath);
        }
        long deleted;
        if (objectPath == null || objectPath.isBlank()) {
            deleted = jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM event_history WHERE occurred_at < ?");
                JdbcUtcTimestamps.bind(statement, 1, cutoff);
                return statement;
            });
        } else {
            String path = objectPath;
            deleted = jdbcTemplate.update(connection -> {
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
        recordCounter.recordDeleted(deleted);
        return deleted;
    }

    /** Bounded batches so a multi-year purge does not hold one giant transaction lock. */
    private long deleteOlderThanMssql(Instant cutoff, String objectPath) {
        long total = 0;
        int deleted;
        boolean global = objectPath == null || objectPath.isBlank();
        do {
            if (global) {
                deleted = jdbcTemplate.update(connection -> {
                    java.sql.PreparedStatement statement = connection.prepareStatement(
                            "DELETE TOP (5000) FROM event_history WHERE occurred_at < ?");
                    JdbcUtcTimestamps.bind(statement, 1, cutoff);
                    return statement;
                });
            } else {
                String path = objectPath;
                deleted = jdbcTemplate.update(connection -> {
                    java.sql.PreparedStatement statement = connection.prepareStatement(
                            """
                                    DELETE TOP (5000) FROM event_history
                                    WHERE occurred_at < ?
                                      AND (object_path = ? OR object_path LIKE ?)
                                    """);
                    JdbcUtcTimestamps.bind(statement, 1, cutoff);
                    statement.setString(2, path);
                    statement.setString(3, path + ".%");
                    return statement;
                });
            }
            total += deleted;
        } while (deleted == 5000);
        recordCounter.recordDeleted(total);
        return total;
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
