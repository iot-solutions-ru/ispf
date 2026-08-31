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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    BindingRuleEngine bindingRuleEngine;

    @Mock
    BindingRulesService bindingRulesService;

    private static final String OBJECT_PATH = "root.platform.devices.flaky-periodic";
    private static final String RULE_ID = "rule-flaky";
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

    @Test
    void runtimeFailureAdvancesConsecutiveCountWithoutDeleting() throws Exception {
        BindingPeriodicScheduleRegistry registry =
                new BindingPeriodicScheduleRegistry(jdbcTemplate, null, 5);
        stubDueQuery(OBJECT_PATH, RULE_ID);
        doThrow(new RuntimeException("engine failure"))
                .when(bindingRuleEngine).onPeriodic(OBJECT_PATH, RULE_ID);

        registry.fireDue(NOW, bindingRuleEngine);

        assertThat(registry.consecutiveFailureCount(OBJECT_PATH, RULE_ID)).isEqualTo(1);
        assertThat(registry.isDisabledAfterFailures(OBJECT_PATH, RULE_ID)).isFalse();
        verify(jdbcTemplate, never()).update(anyString(), eq(OBJECT_PATH), eq(RULE_ID));
    }

    @Test
    void afterMaxFailuresScheduleDeletedAndSkipSetArmed() throws Exception {
        int maxFailures = 3;
        BindingPeriodicScheduleRegistry registry =
                new BindingPeriodicScheduleRegistry(jdbcTemplate, bindingRulesService, maxFailures);
        stubDueQuery(OBJECT_PATH, RULE_ID);
        doThrow(new RuntimeException("engine failure"))
                .when(bindingRuleEngine).onPeriodic(OBJECT_PATH, RULE_ID);
        BindingRule rule = sampleRule(RULE_ID, true);
        when(bindingRulesService.listRules(OBJECT_PATH)).thenReturn(List.of(rule));
        when(bindingRulesService.upsertRule(eq(OBJECT_PATH), any(BindingRule.class))).thenReturn(rule.withEnabled(false));

        for (int i = 0; i < maxFailures - 1; i++) {
            registry.fireDue(NOW, bindingRuleEngine);
        }
        assertThat(registry.consecutiveFailureCount(OBJECT_PATH, RULE_ID)).isEqualTo(maxFailures - 1);
        assertThat(registry.isDisabledAfterFailures(OBJECT_PATH, RULE_ID)).isFalse();

        registry.fireDue(NOW, bindingRuleEngine);

        assertThat(registry.consecutiveFailureCount(OBJECT_PATH, RULE_ID)).isZero();
        assertThat(registry.isDisabledAfterFailures(OBJECT_PATH, RULE_ID)).isTrue();
        verify(jdbcTemplate, atLeastOnce()).update(
                anyString(),
                eq(OBJECT_PATH),
                eq(RULE_ID)
        );
        ArgumentCaptor<BindingRule> ruleCaptor = ArgumentCaptor.forClass(BindingRule.class);
        verify(bindingRulesService).upsertRule(eq(OBJECT_PATH), ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().enabled()).isFalse();
        assertThat(ruleCaptor.getValue().id()).isEqualTo(RULE_ID);
    }

    @Test
    void successResetsConsecutiveFailureCount() throws Exception {
        BindingPeriodicScheduleRegistry registry =
                new BindingPeriodicScheduleRegistry(jdbcTemplate, null, 5);
        stubDueQuery(OBJECT_PATH, RULE_ID);
        doThrow(new RuntimeException("engine failure"))
                .doThrow(new RuntimeException("engine failure"))
                .doNothing()
                .when(bindingRuleEngine).onPeriodic(OBJECT_PATH, RULE_ID);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

        registry.fireDue(NOW, bindingRuleEngine);
        registry.fireDue(NOW, bindingRuleEngine);
        assertThat(registry.consecutiveFailureCount(OBJECT_PATH, RULE_ID)).isEqualTo(2);

        registry.fireDue(NOW, bindingRuleEngine);
        assertThat(registry.consecutiveFailureCount(OBJECT_PATH, RULE_ID)).isZero();
        assertThat(registry.isDisabledAfterFailures(OBJECT_PATH, RULE_ID)).isFalse();
    }

    @Test
    void disabledSkipSetPreventsOnPeriodicAndDeletesRow() throws Exception {
        BindingPeriodicScheduleRegistry registry =
                new BindingPeriodicScheduleRegistry(jdbcTemplate, null, 1);
        stubDueQuery(OBJECT_PATH, RULE_ID);
        doThrow(new RuntimeException("engine failure"))
                .when(bindingRuleEngine).onPeriodic(OBJECT_PATH, RULE_ID);

        registry.fireDue(NOW, bindingRuleEngine);
        assertThat(registry.isDisabledAfterFailures(OBJECT_PATH, RULE_ID)).isTrue();

        registry.fireDue(NOW, bindingRuleEngine);

        verify(bindingRuleEngine, times(1)).onPeriodic(OBJECT_PATH, RULE_ID);
    }

    private void stubDueQuery(String objectPath, String ruleId) throws Exception {
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                eq(Timestamp.from(NOW))
        )).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("object_path")).thenReturn(objectPath);
            when(rs.getString("rule_id")).thenReturn(ruleId);
            when(rs.getLong("periodic_ms")).thenReturn(500L);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    private static BindingRule sampleRule(String id, boolean enabled) {
        return new BindingRule(
                id,
                id,
                enabled,
                0,
                new BindingActivators(false, List.of(), null, 500L),
                "",
                "1.0",
                new BindingTarget("ignored", "value")
        );
    }
}
