package com.ispf.server.application.schedule;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.core.model.DataRecord;
import com.ispf.server.function.FunctionService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.schedule.ScheduleObjectService.ScheduleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSchedulerServiceGateTest {

    @Mock
    JdbcTemplate jdbcTemplate;
    @Mock
    FunctionService functionService;
    @Mock
    DriverRuntimeService driverRuntimeService;
    @Mock
    PlatformLeaderLockService leaderLockService;
    @Mock
    ScheduleObjectService scheduleObjectService;
    @Mock
    ClusterProperties clusterProperties;
    @Mock
    ObjectManager objectManager;

    private PlatformSchedulerService scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PlatformSchedulerService(
                jdbcTemplate,
                functionService,
                driverRuntimeService,
                new ObjectMapper(),
                leaderLockService,
                scheduleObjectService,
                clusterProperties,
                objectManager
        );
    }

    @Test
    void tickSkipsWhenObjectTreeNotReady() {
        when(clusterProperties.isSchedulerActive()).thenReturn(true);
        when(objectManager.isInitialized()).thenReturn(false);

        scheduler.tick();

        verify(leaderLockService, never()).runIfLeader(any(), any(), any());
    }

    @Test
    void failedTreeScheduleRecordsErrorWithoutAdvancingLastTick() {
        ScheduleDefinition schedule = new ScheduleDefinition(
                "schedules.demo",
                "demo",
                true,
                60_000L,
                null,
                null,
                "invoke_function",
                "{\"objectPath\":\"objects.a\",\"functionName\":\"run\",\"input\":{\"k\":\"v\"}}",
                null,
                null
        );
        when(scheduleObjectService.listEnabled()).thenReturn(List.of(schedule));
        when(jdbcTemplate.queryForList("SELECT * FROM platform_schedules WHERE enabled = TRUE"))
                .thenReturn(List.of());
        when(functionService.invoke(anyString(), anyString(), any(DataRecord.class)))
                .thenThrow(new IllegalStateException("boom"));

        scheduler.tickSchedules();

        verify(scheduleObjectService).recordError("schedules.demo", "boom");
        verify(scheduleObjectService, never()).recordTick(any(), any(), any());
    }

    @Test
    void successfulTreeScheduleAdvancesLastTick() {
        ScheduleDefinition schedule = new ScheduleDefinition(
                "schedules.demo",
                "demo",
                true,
                60_000L,
                null,
                null,
                "invoke_function",
                "{\"objectPath\":\"objects.a\",\"functionName\":\"run\",\"input\":{\"k\":\"v\"}}",
                null,
                null
        );
        when(scheduleObjectService.listEnabled()).thenReturn(List.of(schedule));
        when(jdbcTemplate.queryForList("SELECT * FROM platform_schedules WHERE enabled = TRUE"))
                .thenReturn(List.of());

        scheduler.tickSchedules();

        verify(scheduleObjectService).recordTick(eq("schedules.demo"), any(Instant.class), isNull());
        verify(scheduleObjectService, never()).recordError(any(), any());
    }

    @Test
    void failedLegacyScheduleRecordsErrorWithoutAdvancingLastTick() {
        when(scheduleObjectService.listEnabled()).thenReturn(List.of());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("schedule_id", "legacy-1");
        row.put("interval_ms", 60_000L);
        row.put("last_tick_at", Timestamp.from(Instant.now().minusSeconds(3600)));
        row.put("action_type", "invoke_function");
        row.put("action_json", "{\"objectPath\":\"objects.a\",\"functionName\":\"run\",\"input\":{\"k\":\"v\"}}");
        when(jdbcTemplate.queryForList("SELECT * FROM platform_schedules WHERE enabled = TRUE"))
                .thenReturn(List.of(row));
        when(functionService.invoke(anyString(), anyString(), any(DataRecord.class)))
                .thenThrow(new IllegalStateException("boom"));

        scheduler.tickSchedules();

        verify(jdbcTemplate).update(
                eq("UPDATE platform_schedules SET last_error = ? WHERE schedule_id = ?"),
                eq("boom"),
                eq("legacy-1")
        );
        verify(jdbcTemplate, never()).update(contains("last_tick_at"), any(), any());
    }
}
