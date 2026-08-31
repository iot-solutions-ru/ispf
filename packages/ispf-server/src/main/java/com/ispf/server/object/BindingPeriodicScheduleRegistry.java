package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.object.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC index of binding rules with {@code periodicMs > 0} for efficient wake scheduling.
 * Source of truth remains {@code @bindingRules} on each object.
 */
@Service
public class BindingPeriodicScheduleRegistry {

    private static final Logger log = LoggerFactory.getLogger(BindingPeriodicScheduleRegistry.class);
    static final int DEFAULT_MAX_CONSECUTIVE_FAILURES = 5;

    private final JdbcTemplate jdbcTemplate;
    private final BindingRulesService bindingRulesService;
    private final int maxConsecutiveFailures;
    /** Consecutive {@link RuntimeException}s from {@code onPeriodic} per objectPath+ruleId. */
    private final ConcurrentHashMap<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    /**
     * Rules disabled after N consecutive failures for this JVM.
     * Persist {@code enabled=false} via {@link BindingRulesService} when available;
     * this set is the fallback so a re-synced schedule row cannot hot-loop until restart.
     */
    private final Set<String> disabledAfterFailures = ConcurrentHashMap.newKeySet();

    /** Test / minimal construction without Spring. */
    public BindingPeriodicScheduleRegistry(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, DEFAULT_MAX_CONSECUTIVE_FAILURES);
    }

    @Autowired
    public BindingPeriodicScheduleRegistry(
            JdbcTemplate jdbcTemplate,
            @Lazy BindingRulesService bindingRulesService,
            @Value("${ispf.binding.periodic.max-consecutive-failures:5}") int maxConsecutiveFailures
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.bindingRulesService = bindingRulesService;
        this.maxConsecutiveFailures = Math.max(1, maxConsecutiveFailures);
    }

    public void syncObject(String objectPath, List<BindingRule> rules) {
        Set<String> periodicRuleIds = new HashSet<>();
        Instant now = Instant.now();
        for (BindingRule rule : rules) {
            if (!isPeriodicRule(rule)) {
                continue;
            }
            periodicRuleIds.add(rule.id());
            // Re-enable path: operator saved an enabled periodic rule again.
            clearFailureState(objectPath, rule.id());
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
        consecutiveFailures.keySet().removeIf(key -> keyPathMatchesSubtree(key, objectPath));
        disabledAfterFailures.removeIf(key -> keyPathMatchesSubtree(key, objectPath));
    }

    public void clearAll() {
        jdbcTemplate.update("DELETE FROM platform_binding_periodic_rules");
        consecutiveFailures.clear();
        disabledAfterFailures.clear();
    }

    public List<String> objectPathsWithBindingRules() {
        return jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT v.object_path
                        FROM object_variables v
                        INNER JOIN object_nodes n ON n.path = v.object_path
                        WHERE v.name = '@bindingRules'
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
            String key = scheduleKey(dueRule.objectPath(), dueRule.ruleId());
            if (disabledAfterFailures.contains(key)) {
                deleteScheduleRow(dueRule.objectPath(), dueRule.ruleId());
                continue;
            }
            try {
                bindingRuleEngine.onPeriodic(dueRule.objectPath(), dueRule.ruleId());
            } catch (ObjectNotFoundException ex) {
                // Stale schedule rows for deleted objects must not abort the remainder of the tick.
                log.warn(
                        "Removing periodic binding schedule for missing object {}.{}: {}",
                        dueRule.objectPath(),
                        dueRule.ruleId(),
                        ex.getMessage()
                );
                deleteScheduleRow(dueRule.objectPath(), dueRule.ruleId());
                clearFailureState(dueRule.objectPath(), dueRule.ruleId());
                continue;
            } catch (RuntimeException ex) {
                int failures = consecutiveFailures.merge(key, 1, Integer::sum);
                log.warn(
                        "Skipping periodic binding {}.{} (consecutiveFailures={}/{}): {}",
                        dueRule.objectPath(),
                        dueRule.ruleId(),
                        failures,
                        maxConsecutiveFailures,
                        ex.getMessage()
                );
                if (failures >= maxConsecutiveFailures) {
                    disableAfterConsecutiveFailures(dueRule);
                }
                continue;
            }
            consecutiveFailures.remove(key);
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

    /** Visible for tests. */
    int consecutiveFailureCount(String objectPath, String ruleId) {
        return consecutiveFailures.getOrDefault(scheduleKey(objectPath, ruleId), 0);
    }

    /** Visible for tests. */
    boolean isDisabledAfterFailures(String objectPath, String ruleId) {
        return disabledAfterFailures.contains(scheduleKey(objectPath, ruleId));
    }

    private void disableAfterConsecutiveFailures(DueRule dueRule) {
        String key = scheduleKey(dueRule.objectPath(), dueRule.ruleId());
        boolean firstDisable = disabledAfterFailures.add(key);
        deleteScheduleRow(dueRule.objectPath(), dueRule.ruleId());
        consecutiveFailures.remove(key);
        if (firstDisable) {
            log.warn(
                    "Disabling periodic binding {}.{} after {} consecutive failures",
                    dueRule.objectPath(),
                    dueRule.ruleId(),
                    maxConsecutiveFailures
            );
        }
        tryPersistRuleDisabled(dueRule);
    }

    private void tryPersistRuleDisabled(DueRule dueRule) {
        if (bindingRulesService == null) {
            return;
        }
        try {
            List<BindingRule> rules = bindingRulesService.listRules(dueRule.objectPath());
            BindingRule match = null;
            for (BindingRule rule : rules) {
                if (dueRule.ruleId().equals(rule.id())) {
                    match = rule;
                    break;
                }
            }
            if (match == null || !match.enabled()) {
                return;
            }
            bindingRulesService.upsertRule(dueRule.objectPath(), match.withEnabled(false));
        } catch (RuntimeException ex) {
            // Follow-up: ensure enabled=false always persists; JVM skip set prevents re-fire until restart.
            log.warn(
                    "Could not persist enabled=false for periodic binding {}.{} (JVM skip set active): {}",
                    dueRule.objectPath(),
                    dueRule.ruleId(),
                    ex.getMessage()
            );
        }
    }

    private void deleteScheduleRow(String objectPath, String ruleId) {
        jdbcTemplate.update(
                """
                        DELETE FROM platform_binding_periodic_rules
                        WHERE object_path = ? AND rule_id = ?
                        """,
                objectPath,
                ruleId
        );
    }

    private void clearFailureState(String objectPath, String ruleId) {
        String key = scheduleKey(objectPath, ruleId);
        consecutiveFailures.remove(key);
        disabledAfterFailures.remove(key);
    }

    private static String scheduleKey(String objectPath, String ruleId) {
        return objectPath + '\0' + ruleId;
    }

    private static boolean keyPathMatchesSubtree(String key, String objectPath) {
        int sep = key.indexOf('\0');
        String path = sep >= 0 ? key.substring(0, sep) : key;
        return path.equals(objectPath) || path.startsWith(objectPath + ".");
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
