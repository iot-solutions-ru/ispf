package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingPeriodicScheduleRegistryFireDueSoftFailTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    BindingRuleEngine bindingRuleEngine;

    @Test
    void failingRuleIsRescheduledAndTheTickContinues() throws Exception {
        BindingPeriodicScheduleRegistry registry = new BindingPeriodicScheduleRegistry(jdbcTemplate);
        Instant now = Instant.parse("2026-08-31T12:00:00Z");
        String objectPath = "root.platform.devices.flaky-periodic";

        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq(Timestamp.from(now))
        )).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("object_path")).thenReturn(objectPath);
            when(rs.getString("rule_id")).thenReturn("rule-flaky", "rule-ok");
            when(rs.getLong("periodic_ms")).thenReturn(500L);
            return List.of(mapper.mapRow(rs, 0), mapper.mapRow(rs, 1));
        });
        doThrow(new RuntimeException("engine failure"))
                .when(bindingRuleEngine).onPeriodic(objectPath, "rule-flaky");

        registry.fireDue(now, bindingRuleEngine);

        verify(bindingRuleEngine).onPeriodic(objectPath, "rule-ok");
        verify(jdbcTemplate).update(
                contains("SET next_run_at = ?"),
                eq(Timestamp.from(now.plusMillis(500))),
                eq(objectPath),
                eq("rule-flaky")
        );
        verify(jdbcTemplate).update(
                contains("SET last_run_at = ?, next_run_at = ?"),
                eq(Timestamp.from(now)),
                eq(Timestamp.from(now.plusMillis(500))),
                eq(objectPath),
                eq("rule-ok")
        );
    }
}
