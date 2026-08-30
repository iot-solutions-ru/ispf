package com.ispf.server.application.schedule;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.function.FunctionInvocationScope;
import com.ispf.server.function.FunctionService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.schedule.ScheduleDueChecker;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformSchedulerService {

    private static final String SCHEDULER_LOCK = "platform_scheduler";

    private final JdbcTemplate jdbcTemplate;
    private final FunctionService functionService;
    private final DriverRuntimeService driverRuntimeService;
    private final ObjectMapper objectMapper;
    private final PlatformLeaderLockService leaderLockService;
    private final ScheduleObjectService scheduleObjectService;
    private final ClusterProperties clusterProperties;
    private final ObjectManager objectManager;

    public PlatformSchedulerService(
            JdbcTemplate jdbcTemplate,
            FunctionService functionService,
            DriverRuntimeService driverRuntimeService,
            ObjectMapper objectMapper,
            PlatformLeaderLockService leaderLockService,
            ScheduleObjectService scheduleObjectService,
            ClusterProperties clusterProperties,
            ObjectManager objectManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.functionService = functionService;
        this.driverRuntimeService = driverRuntimeService;
        this.objectMapper = objectMapper;
        this.leaderLockService = leaderLockService;
        this.scheduleObjectService = scheduleObjectService;
        this.clusterProperties = clusterProperties;
        this.objectManager = objectManager;
    }

    public void upsert(PlatformSchedule schedule) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_schedules WHERE schedule_id = ?",
                Integer.class,
                schedule.scheduleId()
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE platform_schedules
                    SET app_id = ?, enabled = ?, interval_ms = ?, action_type = ?, action_json = ?
                    WHERE schedule_id = ?
                    """,
                    schedule.appId(),
                    schedule.enabled(),
                    schedule.intervalMs(),
                    schedule.actionType(),
                    schedule.actionJson(),
                    schedule.scheduleId()
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO platform_schedules (
                    schedule_id, app_id, enabled, interval_ms, action_type, action_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                schedule.scheduleId(),
                schedule.appId(),
                schedule.enabled(),
                schedule.intervalMs(),
                schedule.actionType(),
                schedule.actionJson(),
                Timestamp.from(Instant.now())
        );
    }

    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList("""
                SELECT schedule_id, app_id, enabled, interval_ms, action_type, action_json,
                       last_tick_at, last_error
                FROM platform_schedules
                ORDER BY schedule_id
                """);
    }

    @Scheduled(fixedDelay = 5000)
    public void tick() {
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!objectManager.isInitialized()) {
            return;
        }
        if (!leaderLockService.tryAcquire(SCHEDULER_LOCK, Duration.ofSeconds(30))) {
            return;
        }
        try {
            tickSchedules();
        } finally {
            leaderLockService.release(SCHEDULER_LOCK);
        }
    }

    void tickSchedules() {
        tickTreeSchedules();
        tickLegacySchedules();
    }

    private void tickTreeSchedules() {
        Instant now = Instant.now();
        for (com.ispf.server.schedule.ScheduleObjectService.ScheduleDefinition schedule : scheduleObjectService.listEnabled()) {
            if (!ScheduleDueChecker.isDue(
                    now,
                    schedule.lastTickAt(),
                    schedule.intervalMs(),
                    schedule.cronExpression(),
                    schedule.timeZone()
            )) {
                continue;
            }
            try {
                executeAction(schedule.actionType(), schedule.actionJson());
                scheduleObjectService.recordTick(schedule.path(), now, null);
            } catch (Exception ex) {
                scheduleObjectService.recordTick(schedule.path(), now, ex.getMessage());
            }
        }
    }

    private void tickLegacySchedules() {
        List<Map<String, Object>> schedules = jdbcTemplate.queryForList(
                "SELECT * FROM platform_schedules WHERE enabled = TRUE"
        );
        Instant now = Instant.now();
        for (Map<String, Object> schedule : schedules) {
            long intervalMs = ((Number) schedule.get("interval_ms")).longValue();
            Instant lastTick = toInstant(schedule.get("last_tick_at"));
            if (lastTick != null && lastTick.plusMillis(intervalMs).isAfter(now)) {
                continue;
            }
            String scheduleId = String.valueOf(schedule.get("schedule_id"));
            try {
                executeAction(String.valueOf(schedule.get("action_type")), String.valueOf(schedule.get("action_json")));
                jdbcTemplate.update(
                        "UPDATE platform_schedules SET last_tick_at = ?, last_error = NULL WHERE schedule_id = ?",
                        Timestamp.from(now),
                        scheduleId
                );
            } catch (Exception ex) {
                jdbcTemplate.update(
                        "UPDATE platform_schedules SET last_tick_at = ?, last_error = ? WHERE schedule_id = ?",
                        Timestamp.from(now),
                        ex.getMessage(),
                        scheduleId
                );
            }
        }
    }

    private void executeAction(String actionType, String actionJson) throws Exception {
        if ("invoke_function".equals(actionType)) {
            executeInvokeFunction(actionJson);
            return;
        }
        if ("write_point".equals(actionType)) {
            executeWritePoint(actionJson);
        }
    }

    private void executeInvokeFunction(String actionJson) throws Exception {
        Map<?, ?> action = objectMapper.readValue(actionJson, Map.class);
        String objectPath = String.valueOf(action.get("objectPath"));
        String functionName = String.valueOf(action.get("functionName"));
        DataRecord input = null;
        if (action.get("input") instanceof Map<?, ?> inputMap && !inputMap.isEmpty()) {
            DataSchema.Builder schemaBuilder = DataSchema.builder("scheduleInput");
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                schemaBuilder.field(key, FieldType.STRING);
                row.put(key, entry.getValue());
            }
            input = DataRecord.single(schemaBuilder.build(), row);
        }
        final DataRecord invokeInput = input;
        FunctionInvocationScope.runSystemTrusted(() ->
                functionService.invoke(objectPath, functionName, invokeInput));
    }

    private void executeWritePoint(String actionJson) throws Exception {
        Map<?, ?> action = objectMapper.readValue(actionJson, Map.class);
        String objectPath = String.valueOf(action.get("objectPath"));
        String pointId = String.valueOf(action.get("pointId"));
        if (objectPath == null || objectPath.isBlank() || "null".equals(objectPath)) {
            throw new IllegalArgumentException("write_point requires objectPath");
        }
        if (pointId == null || pointId.isBlank() || "null".equals(pointId)) {
            throw new IllegalArgumentException("write_point requires pointId");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        Object fieldsRaw = action.get("fields");
        if (fieldsRaw instanceof Map<?, ?> fieldsMap) {
            for (Map.Entry<?, ?> entry : fieldsMap.entrySet()) {
                fields.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } else if (action.get("value") != null) {
            fields.put("value", action.get("value"));
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("write_point requires fields or value");
        }
        DataSchema.Builder schemaBuilder = DataSchema.builder("scheduleWrite");
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            schemaBuilder.field(entry.getKey(), FieldType.STRING);
            row.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        driverRuntimeService.writePoint(objectPath, pointId, DataRecord.single(schemaBuilder.build(), row));
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }

    public int countSchedules() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_schedules",
                Integer.class
        );
        return count != null ? count : 0;
    }

    public int countEnabledSchedules() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_schedules WHERE enabled = TRUE",
                Integer.class
        );
        return count != null ? count : 0;
    }

    public record PlatformSchedule(
            String scheduleId,
            String appId,
            boolean enabled,
            long intervalMs,
            String actionType,
            String actionJson
    ) {
    }
}
