package com.ispf.server.application.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.function.FunctionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformSchedulerService {

    private final JdbcTemplate jdbcTemplate;
    private final FunctionService functionService;
    private final ObjectMapper objectMapper;

    public PlatformSchedulerService(
            JdbcTemplate jdbcTemplate,
            FunctionService functionService,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.functionService = functionService;
        this.objectMapper = objectMapper;
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
        if (!"invoke_function".equals(actionType)) {
            return;
        }
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
        functionService.invoke(objectPath, functionName, input);
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
