package com.ispf.server.history;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

/**
 * Batched JDBC inserts for historian — avoids per-row JPA {@code IDENTITY} round-trips.
 */
@Service
@ConditionalOnProperty(name = "ispf.variable-history.store", havingValue = "jdbc", matchIfMissing = true)
public class JdbcVariableHistoryWriteStore implements VariableHistoryWriteStore {

    private static final String INSERT_SQL = """
            INSERT INTO variable_samples (object_path, variable_name, field_name, sampled_at, value_double, value_text)
            VALUES (?, ?, ?, ?, ?, ?)
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
        jdbcTemplate.update(
                INSERT_SQL,
                record.objectPath(),
                record.variableName(),
                record.fieldName(),
                Timestamp.from(record.sampledAt()),
                record.valueDouble(),
                record.valueText()
        );
    }

    private static void bindInsert(java.sql.PreparedStatement statement, VariableHistoryWriteRecord record)
            throws java.sql.SQLException {
        statement.setString(1, record.objectPath());
        statement.setString(2, record.variableName());
        statement.setString(3, record.fieldName());
        statement.setTimestamp(4, Timestamp.from(record.sampledAt()));
        if (record.valueDouble() != null) {
            statement.setDouble(5, record.valueDouble());
        } else {
            statement.setNull(5, Types.DOUBLE);
        }
        if (record.valueText() != null) {
            statement.setString(6, record.valueText());
        } else {
            statement.setNull(6, Types.VARCHAR);
        }
    }
}
