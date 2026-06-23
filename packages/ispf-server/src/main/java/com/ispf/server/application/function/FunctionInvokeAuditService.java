package com.ispf.server.application.function;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FunctionInvokeAuditService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final JdbcTemplate jdbcTemplate;
    private final String auditTable;

    private static final RowMapper<FunctionInvokeAuditEntry> ROW_MAPPER = (rs, rowNum) -> new FunctionInvokeAuditEntry(
            rs.getObject("id", UUID.class),
            rs.getString("correlation_id"),
            rs.getString("object_path"),
            rs.getString("function_name"),
            rs.getString("app_id"),
            rs.getBoolean("success"),
            rs.getString("error_message"),
            rs.getTimestamp("invoked_at").toInstant()
    );

    public FunctionInvokeAuditService(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditTable = platformSqlCatalog.table("function_invoke_audit");
    }

    public void record(String appId, String objectPath, String functionName, boolean success, String errorMessage) {
        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, correlation_id, object_path, function_name, app_id, success, error_message, invoked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(auditTable),
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                objectPath,
                functionName,
                appId,
                success,
                truncate(errorMessage),
                Timestamp.from(Instant.now())
        );
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
    }

    public List<FunctionInvokeAuditEntry> list(
            String objectPath,
            String functionName,
            Boolean success,
            int limit
    ) {
        int cappedLimit = Math.max(1, Math.min(limit, 200));
        StringBuilder sql = new StringBuilder("""
                SELECT id, correlation_id, object_path, function_name, app_id, success, error_message, invoked_at
                FROM %s
                WHERE 1=1
                """.formatted(auditTable));
        List<Object> args = new ArrayList<>();
        if (objectPath != null && !objectPath.isBlank()) {
            sql.append(" AND object_path = ?");
            args.add(objectPath);
        }
        if (functionName != null && !functionName.isBlank()) {
            sql.append(" AND function_name = ?");
            args.add(functionName);
        }
        if (success != null) {
            sql.append(" AND success = ?");
            args.add(success);
        }
        sql.append(" ORDER BY invoked_at DESC LIMIT ?");
        args.add(cappedLimit);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }
}
