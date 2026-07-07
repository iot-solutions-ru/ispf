package com.ispf.server.platform;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.relational.RelationalDialect;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-backed distributed job queue (ADR-0031).
 */
@Service
public class PlatformJobService {

    public static final String TYPE_REPORT_RUN = "report_run";

    public enum JobStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ClusterProperties clusterProperties;
    private final RelationalDialect relationalDialect;
    private final String instanceId;

    public PlatformJobService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ClusterProperties clusterProperties,
            NatsProperties natsProperties,
            RelationalDialect relationalDialect
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clusterProperties = clusterProperties;
        this.relationalDialect = relationalDialect;
        this.instanceId = natsProperties.replicaId();
    }

    @Transactional
    public UUID submitReportRun(String reportPath, Map<String, Object> parameters, String createdBy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", reportPath);
        payload.put("parameters", parameters == null ? Map.of() : parameters);
        return submit(TYPE_REPORT_RUN, payload, createdBy);
    }

    @Transactional
    public UUID submit(String jobType, Map<String, Object> payload, String createdBy) {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                        INSERT INTO platform_jobs
                            (job_id, job_type, status, priority, payload, created_at, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                jobId,
                jobType,
                JobStatus.QUEUED.name(),
                0,
                writeJson(payload),
                Timestamp.from(now),
                createdBy
        );
        return jobId;
    }

    public Optional<JobView> getJob(UUID jobId) {
        List<JobView> rows = jdbcTemplate.query(
                """
                        SELECT job_id, job_type, status, payload, result, error_message,
                               holder_id, created_at, started_at, completed_at, created_by
                        FROM platform_jobs
                        WHERE job_id = ?
                        """,
                (rs, rowNum) -> mapRow(rs),
                jobId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public int recoverExpiredRunningJobs() {
        Instant now = Instant.now();
        return jdbcTemplate.update(
                """
                        UPDATE platform_jobs
                        SET status = ?, holder_id = NULL, expires_at = NULL,
                            started_at = NULL, error_message = NULL
                        WHERE status = ? AND expires_at IS NOT NULL AND expires_at <= ?
                        """,
                JobStatus.QUEUED.name(),
                JobStatus.RUNNING.name(),
                Timestamp.from(now)
        );
    }

    @Transactional
    public Optional<ClaimedJob> claimNextJob() {
        Instant now = Instant.now();
        Duration runningTtl = Duration.ofSeconds(clusterProperties.jobRunningTtlSeconds());
        Instant expiresAt = now.plus(runningTtl);

        List<UUID> candidates = jdbcTemplate.query(
                relationalDialect.queuedPlatformJobSelectSql(),
                (rs, rowNum) -> (UUID) rs.getObject("job_id"),
                JobStatus.QUEUED.name()
        );
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        UUID jobId = candidates.getFirst();
        int updated = jdbcTemplate.update(
                """
                        UPDATE platform_jobs
                        SET status = ?, holder_id = ?, started_at = ?, expires_at = ?
                        WHERE job_id = ? AND status = ?
                        """,
                JobStatus.RUNNING.name(),
                instanceId,
                Timestamp.from(now),
                Timestamp.from(expiresAt),
                jobId,
                JobStatus.QUEUED.name()
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return getJob(jobId).map(job -> new ClaimedJob(
                job.jobId(),
                job.jobType(),
                readJsonMap(job.payload())
        ));
    }

    @Transactional
    public void completeJob(UUID jobId, Map<String, Object> result) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                        UPDATE platform_jobs
                        SET status = ?, result = ?, error_message = NULL,
                            completed_at = ?, expires_at = NULL
                        WHERE job_id = ? AND holder_id = ? AND status = ?
                        """,
                JobStatus.COMPLETED.name(),
                writeJson(result),
                Timestamp.from(now),
                jobId,
                instanceId,
                JobStatus.RUNNING.name()
        );
    }

    @Transactional
    public void failJob(UUID jobId, String errorMessage) {
        Instant now = Instant.now();
        String message = truncate(errorMessage, 4000);
        jdbcTemplate.update(
                """
                        UPDATE platform_jobs
                        SET status = ?, error_message = ?, completed_at = ?, expires_at = NULL
                        WHERE job_id = ? AND holder_id = ? AND status = ?
                        """,
                JobStatus.FAILED.name(),
                message,
                Timestamp.from(now),
                jobId,
                instanceId,
                JobStatus.RUNNING.name()
        );
    }

    public String instanceId() {
        return instanceId;
    }

    public List<JobView> listRunningByHolder(String holderId) {
        return jdbcTemplate.query(
                """
                        SELECT job_id, job_type, status, payload, result, error_message,
                               holder_id, created_at, started_at, completed_at, created_by
                        FROM platform_jobs
                        WHERE status = ? AND holder_id = ?
                        ORDER BY started_at NULLS LAST, created_at
                        """,
                (rs, rowNum) -> mapRow(rs),
                JobStatus.RUNNING.name(),
                holderId
        );
    }

    public Map<String, Object> readPayloadMap(JobView job) {
        return readJsonMap(job.payload());
    }

    private JobView mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new JobView(
                (UUID) rs.getObject("job_id"),
                rs.getString("job_type"),
                rs.getString("status"),
                rs.getString("payload"),
                rs.getString("result"),
                rs.getString("error_message"),
                rs.getString("holder_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                rs.getString("created_by")
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize job JSON", ex);
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse job payload JSON", ex);
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    public record JobView(
            UUID jobId,
            String jobType,
            String status,
            String payload,
            String result,
            String errorMessage,
            String holderId,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            String createdBy
    ) {
        public Map<String, Object> toApiMap(ObjectMapper objectMapper) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("jobId", jobId.toString());
            map.put("jobType", jobType);
            map.put("status", status);
            map.put("holderId", holderId);
            map.put("createdAt", createdAt.toString());
            if (startedAt != null) {
                map.put("startedAt", startedAt.toString());
            }
            if (completedAt != null) {
                map.put("completedAt", completedAt.toString());
            }
            if (createdBy != null) {
                map.put("createdBy", createdBy);
            }
            if (errorMessage != null) {
                map.put("errorMessage", errorMessage);
            }
            if (result != null && !result.isBlank()) {
                try {
                    map.put("result", objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {
                    }));
                } catch (Exception ex) {
                    map.put("resultRaw", result);
                }
            }
            return map;
        }
    }

    public record ClaimedJob(UUID jobId, String jobType, Map<String, Object> payload) {
    }
}
