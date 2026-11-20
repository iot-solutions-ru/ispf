package com.ispf.server.history;

import com.ispf.server.relational.JdbcUtcTimestamps;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

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
    private final ObjectProvider<MssqlJdbcHistoryBuffer> mssqlBuffer;

    public JdbcVariableHistoryWriteStore(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    @Autowired
    public JdbcVariableHistoryWriteStore(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<MssqlJdbcHistoryBuffer> mssqlBuffer
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mssqlBuffer = mssqlBuffer;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendBatch(List<VariableHistoryWriteRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        try {
            jdbcTemplate.batchUpdate(
                    INSERT_SQL,
                    records,
                    records.size(),
                    (statement, record) -> bindInsert(statement, record)
            );
        } catch (DataAccessException ex) {
            bufferOnConnectivityLoss(records, ex);
            throw ex;
        }
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
        JdbcUtcTimestamps.bind(statement, 4, record.sampledAt());
        JdbcUtcTimestamps.bind(statement, 5, record.observedAt());
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

    private void bufferOnConnectivityLoss(List<VariableHistoryWriteRecord> records, DataAccessException ex) {
        if (mssqlBuffer == null) {
            return;
        }
        MssqlJdbcHistoryBuffer buffer = mssqlBuffer.getIfAvailable();
        if (buffer == null) {
            return;
        }
        SQLException sql = ex.getMostSpecificCause() instanceof SQLException sqlException
                ? sqlException
                : null;
        if (sql == null || !MssqlJdbcHistoryBuffer.isConnectivity(sql)) {
            return;
        }
        try {
            buffer.accept(records);
        } catch (java.io.IOException io) {
            ex.addSuppressed(io);
        }
    }
}
