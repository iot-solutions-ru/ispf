package com.ispf.server.application.function;

import com.ispf.core.model.DataRecord;
import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.concurrent.ElasticBatchQueueWriter;
import com.ispf.server.config.FunctionProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FunctionInvokeAuditService {

    private static final Logger log = LoggerFactory.getLogger(FunctionInvokeAuditService.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;
    private static final int MAX_JSON_LENGTH = 32_768;

    record PendingRecord(
            String appId,
            String objectPath,
            String functionName,
            boolean success,
            String errorMessage,
            String inputJson,
            String outputJson
    ) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final FunctionProperties properties;
    private final ObjectManager objectManager;
    private final ObjectEntityMapper entityMapper;
    private final String auditTable;

    private ElasticBatchQueueWriter<PendingRecord> batchWriter;

    private final RowMapper<FunctionInvokeAuditEntry> rowMapper = (rs, rowNum) -> new FunctionInvokeAuditEntry(
            rs.getObject("id", UUID.class),
            rs.getString("correlation_id"),
            rs.getString("object_path"),
            rs.getString("function_name"),
            rs.getString("app_id"),
            rs.getBoolean("success"),
            rs.getString("error_message"),
            rs.getString("input_json"),
            rs.getString("output_json"),
            rs.getTimestamp("invoked_at").toInstant()
    );

    public FunctionInvokeAuditService(
            JdbcTemplate jdbcTemplate,
            PlatformSqlCatalog platformSqlCatalog,
            FunctionProperties properties,
            ObjectManager objectManager,
            ObjectEntityMapper entityMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.objectManager = objectManager;
        this.entityMapper = entityMapper;
        this.auditTable = platformSqlCatalog.table("function_invoke_audit");
    }

    @PostConstruct
    void start() {
        FunctionProperties.Audit audit = properties.getAudit();
        if (!audit.isAsyncEnabled()) {
            return;
        }
        batchWriter = new ElasticBatchQueueWriter<>(
                audit.resolvedElastic(),
                audit.getBatchSize(),
                audit.getFlushIntervalMs(),
                "function-audit-writer",
                this::persistBatch
        );
        batchWriter.start(audit.getQueueCapacity());
        log.info(
                "Function audit async writer started (enabled={}, mode={}, queueCapacity={}, batchSize={}, elastic={})",
                audit.isEnabled(),
                audit.getMode(),
                audit.getQueueCapacity(),
                audit.getBatchSize(),
                audit.isElasticWriterEnabled()
        );
    }

    @PreDestroy
    void shutdown() {
        if (batchWriter != null) {
            batchWriter.shutdown();
        }
    }

    public void record(
            String appId,
            String objectPath,
            String functionName,
            DataRecord input,
            DataRecord output,
            boolean success,
            String errorMessage
    ) {
        record(new PendingRecord(
                appId,
                objectPath,
                functionName,
                success,
                errorMessage,
                serializeRecord(input),
                serializeRecord(output)
        ));
    }

    public boolean isMasterEnabled() {
        return properties.getAudit().isEnabled();
    }

    public boolean isObjectAuditEnabled(String objectPath) {
        return isMasterEnabled() && objectManager.isFunctionAuditEnabled(objectPath);
    }

    public FunctionAuditMode auditMode() {
        return properties.getAudit().resolvedMode();
    }

    public List<FunctionInvokeAuditEntry> list(
            String objectPath,
            String functionName,
            Boolean success,
            int limit
    ) {
        int cappedLimit = Math.max(1, Math.min(limit, 200));
        StringBuilder sql = new StringBuilder("""
                SELECT id, correlation_id, object_path, function_name, app_id, success, error_message,
                       input_json, output_json, invoked_at
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
        sql.append(" ORDER BY invoked_at DESC, id DESC LIMIT ?");
        args.add(cappedLimit);
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    private void record(PendingRecord pending) {
        FunctionProperties.Audit audit = properties.getAudit();
        if (!isMasterEnabled()) {
            return;
        }
        if (!objectManager.isFunctionAuditEnabled(pending.objectPath())) {
            return;
        }
        // Per-object opt-in records every invoke; platform mode applies only without object flag (future).
        if (!passesSampleRate(audit.getSampleRate())) {
            return;
        }
        if (audit.isAsyncEnabled() && batchWriter != null) {
            if (!batchWriter.offer(pending)) {
                log.warn("Function audit queue full, dropping record for {}", pending.objectPath());
            }
            return;
        }
        persistOne(pending);
    }

    static boolean shouldRecord(FunctionAuditMode mode, boolean success) {
        return switch (mode) {
            case ERRORS -> !success;
            case ALL -> true;
        };
    }

    private static boolean passesSampleRate(double sampleRate) {
        if (sampleRate >= 1.0) {
            return true;
        }
        if (sampleRate <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private void persistBatch(List<PendingRecord> batch) {
        for (PendingRecord pending : batch) {
            persistOne(pending);
        }
    }

    private void persistOne(PendingRecord pending) {
        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, correlation_id, object_path, function_name, app_id, success, error_message,
                    input_json, output_json, invoked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(auditTable),
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                pending.objectPath(),
                pending.functionName(),
                pending.appId(),
                pending.success(),
                truncate(pending.errorMessage()),
                truncateJson(pending.inputJson()),
                truncateJson(pending.outputJson()),
                Timestamp.from(Instant.now())
        );
    }

    private String serializeRecord(DataRecord record) {
        if (record == null) {
            return null;
        }
        return entityMapper.writeDataRecord(record);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
    }

    private static String truncateJson(String value) {
        if (value == null || value.length() <= MAX_JSON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_JSON_LENGTH - 3) + "...";
    }
}

