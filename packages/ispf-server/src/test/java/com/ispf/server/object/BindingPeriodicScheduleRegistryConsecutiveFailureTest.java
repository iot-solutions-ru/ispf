package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingPeriodicScheduleRegistryConsecutiveFailureTest {

    private static final String OBJECT_PATH = "root.platform.devices.flaky-periodic";
    private static final String RULE_ID = "rule-flaky";
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final long PERIOD_MS = 500L;

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    BindingRuleEngine bindingRuleEngine;

    @Test
    void failuresBackOffExponentiallyUpToTheCap() throws Exception {
        BindingPeriodicScheduleRegistry registry = new BindingPeriodicScheduleRegistry(jdbcTemplate, 3_000L);
        stubDueQuery();
        doThrow(new RuntimeException("engine failure"))
                .when(bindingRuleEngine).onPeriodic(OBJECT_PATH, RULE_ID);

        for (int i = 0; i < 5; i++) {
            registry.fireDue(NOW, bindingRuleEngine);
        }

        assertThat(scheduledDelaysMs()).containsExactly(500L, 1_000L, 2_000L, 3_000L, 3_000L);
        assertThat(registry.consecutiveFailureCount(OBJECT_PATH, RULE_ID)).isEqualTo(5);
        verify(jdbcTemplate, never()).update(contains("last_run_at"), any(), any(), any(), any());
    }

    @Test
    void failingRuleIsNeverDisabled() throws Exception {
        BindingPeriodicScheduleRegistry registry = new BindingPeriodicScheduleRegistry(jdbcTemplate);
        stubDueQuery();
        doThrow(new RuntimeException("engine failure"))
                .when(bindingRuleEngine).onPeriodic(OBJECT_PATH, RULE_ID);

        for (int i = 0; i < 20; i++) {
            registry.fireDue(NOW, bindingRuleEngine);
        }

        verify(bindingRuleEngine, times(20)).onPeriodic(OBJECT_PATH, RULE_ID);
        verify(jdbcTemplate, never()).update(contains("DELETE"), eq(OBJECT_PATH), eq(RULE_ID));
    }

    @Test
    void successResetsTheBackoff() throws Exception {
        BindingPeriodicScheduleRegistry registry = new BindingPeriodicScheduleRegistry(jdbcTemplate, 3_000L);
        stubDueQuery();
        doThrow(new RuntimeException("engine failure"))
                .doThrow(new RuntimeException("engine failure"))
                .doNothing()
                .doThrow(new RuntimeException("engine failure"))
                .when(bindingRuleEngine).onPeriodic(OBJECT_PATH, RULE_ID);

        for (int i = 0; i < 4; i++) {
            registry.fireDue(NOW, bindingRuleEngine);
        }

        assertThat(scheduledDelaysMs()).containsExactly(500L, 1_000L, 500L);
        verify(jdbcTemplate).update(
                contains("SET last_run_at = ?, next_run_at = ?"),
                eq(Timestamp.from(NOW)),
                eq(Timestamp.from(NOW.plusMillis(PERIOD_MS))),
                eq(OBJECT_PATH),
                eq(RULE_ID)
        );
        assertThat(registry.consecutiveFailureCount(OBJECT_PATH, RULE_ID)).isEqualTo(1);
    }

    @Test
    void savingTheRulesResetsTheBackoff() throws Exception {
        BindingPeriodicScheduleRegistry registry = new BindingPeriodicScheduleRegistry(jdbcTemplate);
        stubDueQuery();
        doThrow(new RuntimeException("engine failure"))
                .when(bindingRuleEngine).onPeriodic(OBJECT_PATH, RULE_ID);
        registry.fireDue(NOW, bindingRuleEngine);
        registry.fireDue(NOW, bindingRuleEngine);

        registry.syncObject(OBJECT_PATH, List.of(periodicRule()));

        assertThat(registry.consecutiveFailureCount(OBJECT_PATH, RULE_ID)).isZero();
    }

    @Test
    void backoffNeverExceedsTheLongerOfPeriodAndCap() {
        BindingPeriodicScheduleRegistry registry = new BindingPeriodicScheduleRegistry(jdbcTemplate, 300_000L);

        assertThat(registry.backoffMs(500L, 1)).isEqualTo(500L);
        assertThat(registry.backoffMs(500L, 2)).isEqualTo(1_000L);
        assertThat(registry.backoffMs(500L, 10)).isEqualTo(256_000L);
        assertThat(registry.backoffMs(500L, 11)).isEqualTo(300_000L);
        assertThat(registry.backoffMs(500L, Integer.MAX_VALUE)).isEqualTo(300_000L);
        assertThat(registry.backoffMs(600_000L, 5)).isEqualTo(600_000L);
        assertThat(new BindingPeriodicScheduleRegistry(jdbcTemplate, Long.MAX_VALUE).backoffMs(500L, 200))
                .isEqualTo(Long.MAX_VALUE);
    }

    private List<Long> scheduledDelaysMs() {
        ArgumentCaptor<Object> nextRun = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate, atLeastOnce()).update(
                contains("SET next_run_at = ?"),
                nextRun.capture(),
                eq(OBJECT_PATH),
                eq(RULE_ID)
        );
        return nextRun.getAllValues().stream()
                .map(value -> Duration.between(NOW, ((Timestamp) value).toInstant()).toMillis())
                .toList();
    }

    private void stubDueQuery() throws Exception {
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq(Timestamp.from(NOW))
        )).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("object_path")).thenReturn(OBJECT_PATH);
            when(rs.getString("rule_id")).thenReturn(RULE_ID);
            when(rs.getLong("periodic_ms")).thenReturn(PERIOD_MS);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    private static BindingRule periodicRule() {
        return new BindingRule(
                RULE_ID,
                RULE_ID,
                true,
                0,
                new BindingActivators(false, List.of(), null, PERIOD_MS),
                "",
                "1.0",
                new BindingTarget("ignored", "value")
        );
    }
}
