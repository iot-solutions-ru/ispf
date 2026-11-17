package com.ispf.server.history;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Batched JDBC inserts for historian — avoids per-row JPA {@code IDENTITY} round-trips.
 */
@Service
@ConditionalOnProperty(name = "ispf.variable-history.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcVariableHistoryWriteStore implements VariableHistoryWriteStore {

    private static final String INSERT_SQL = """
            INSERT INTO variable_samples (object_path, variable_name, field_name, sampled_at, observed_at, value_double, value_text, quality)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcVariableHistoryWriteStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                records,
                records.size(),
                (statement, record) -> bindInsert(statement, record)
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendOne(VariableHistoryWriteRecord record) {
        jdbcTemplate.update(INSERT_SQL, statement -> bindInsert(statement, record));
    }

    private static void bindInsert(java.sql.PreparedStatement statement, VariableHistoryWriteRecord record)
            throws java.sql.SQLException {
        statement.setString(1, record.objectPath());
        statement.setString(2, record.variableName());
        statement.setString(3, record.fieldName());
        bindUtcTimestamp(statement, 4, record.sampledAt());
        bindUtcTimestamp(statement, 5, record.observedAt());
        if (record.valueDouble() != null) {
            statement.setDouble(6, record.valueDouble());
        } else {
            statement.setNull(6, Types.DOUBLE);
        }
        if (record.valueText() != null) {
            statement.setString(7, record.valueText());
        } else {
            statement.setNull(7, Types.VARCHAR);
        }
        if (record.quality() != null) {
            statement.setString(8, record.quality());
        } else {
            statement.setNull(8, Types.VARCHAR);
        }
    }

    /**
     * {@code variable_samples} stores {@code TIMESTAMP} without a time zone and Hibernate reads it as UTC.
     * {@link java.sql.PreparedStatement#setTimestamp(int, Timestamp)} sends the JVM zone, so a Moscow
     * clock stores a sample three hours ahead of the instant that was written.
     */
    private static void bindUtcTimestamp(java.sql.PreparedStatement statement, int index, Instant instant)
            throws java.sql.SQLException {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        statement.setTimestamp(index, Timestamp.from(instant), utc);
    }
}
