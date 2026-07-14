package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JDBC index of binding rules with {@code periodicMs > 0} for efficient wake scheduling.
 * Source of truth remains {@code @bindingRules} on each object.
 */
@Service
public class BindingPeriodicScheduleRegistry {

    private final JdbcTemplate jdbcTemplate;

    public BindingPeriodicScheduleRegistry(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void syncObject(String objectPath, List<BindingRule> rules) {
        Set<String> periodicRuleIds = new HashSet<>();
        Instant now = Instant.now();
        for (BindingRule rule : rules) {
            if (!isPeriodicRule(rule)) {
                continue;
            }
            periodicRuleIds.add(rule.id());
            upsertRule(objectPath, rule, now);
        }
        if (periodicRuleIds.isEmpty()) {
            jdbcTemplate.update(
                    "DELETE FROM platform_binding_periodic_rules WHERE object_path = ?",
                    objectPath
            );
            return;
        }
        jdbcTemplate.update(
                """
                        DELETE FROM platform_binding_periodic_rules
                        WHERE object_path = ?
                          AND rule_id NOT IN (%s)
                        """.formatted(placeholders(periodicRuleIds.size())),
                bindArgs(objectPath, periodicRuleIds)
        );
    }

    public void removeSubtree(String objectPath) {
        jdbcTemplate.update(
                """
                        DELETE FROM platform_binding_periodic_rules
                        WHERE object_path = ? OR object_path LIKE ?
                        """,
                objectPath,
                objectPath + ".%"
        );
    }

    public void clearAll() {
        jdbcTemplate.update("DELETE FROM platform_binding_periodic_rules");
    }

    public List<String> objectPathsWithBindingRules() {
        return jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT object_path
                        FROM object_variables
                        WHERE name = '@bindingRules'
                        """,
                String.class
        );
    }

    public int countEnabled() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM platform_binding_periodic_rules
                        WHERE enabled = TRUE AND periodic_ms > 0
                        """,
                Integer.class
        );
        return count != null ? count : 0;
    }

    public Instant nextWakeAt() {
        return jdbcTemplate.query(
                """
                        SELECT MIN(next_run_at) FROM platform_binding_periodic_rules
                        WHERE enabled = TRUE AND periodic_ms > 0
                        """,
                rs -> rs.next() ? toInstant(rs.getTimestamp(1)) : null
        );
    }

    public void fireDue(Instant now, BindingRuleEngine bindingRuleEngine) {
        List<DueRule> dueRules = jdbcTemplate.query(
                """
                        SELECT object_path, rule_id, periodic_ms
                        FROM platform_binding_periodic_rules
                        WHERE enabled = TRUE
                          AND periodic_ms > 0
                          AND next_run_at <= ?
                        ORDER BY next_run_at, object_path, rule_id
                        """,
                (rs, rowNum) -> new DueRule(
                        rs.getString("object_path"),
                        rs.getString("rule_id"),
                        rs.getLong("periodic_ms")
                ),
                Timestamp.from(now)
        );
        for (DueRule dueRule : dueRules) {
            bindingRuleEngine.onPeriodic(dueRule.objectPath(), dueRule.ruleId());
            Instant nextRun = now.plusMillis(dueRule.periodicMs());
            jdbcTemplate.update(
                    """
                            UPDATE platform_binding_periodic_rules
                            SET last_run_at = ?, next_run_at = ?
                            WHERE object_path = ? AND rule_id = ?
                            """,
                    Timestamp.from(now),
                    Timestamp.from(nextRun),
                    dueRule.objectPath(),
                    dueRule.ruleId()
            );
        }
    }

    private void upsertRule(String objectPath, BindingRule rule, Instant now) {
        long periodicMs = rule.activators().periodicMs();
        int updated = jdbcTemplate.update(
                """
                        UPDATE platform_binding_periodic_rules
                        SET periodic_ms = ?, enabled = ?, next_run_at = ?
                        WHERE object_path = ? AND rule_id = ?
                        """,
                periodicMs,
                rule.enabled(),
                Timestamp.from(now),
                objectPath,
                rule.id()
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO platform_binding_periodic_rules (
                                object_path, rule_id, periodic_ms, enabled, last_run_at, next_run_at
                            ) VALUES (?, ?, ?, ?, NULL, ?)
                            """,
                    objectPath,
                    rule.id(),
                    periodicMs,
                    rule.enabled(),
                    Timestamp.from(now)
            );
        }
    }

    private static boolean isPeriodicRule(BindingRule rule) {
        if (rule == null || rule.isHistorian()) {
            return false;
        }
        BindingActivators activators = rule.activators();
        return rule.enabled() && activators != null && activators.hasPeriodicSchedule();
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static Object[] bindArgs(String objectPath, Set<String> ruleIds) {
        Object[] args = new Object[1 + ruleIds.size()];
        args[0] = objectPath;
        int index = 1;
        for (String ruleId : ruleIds) {
            args[index++] = ruleId;
        }
        return args;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    record DueRule(String objectPath, String ruleId, long periodicMs) {
    }
}
