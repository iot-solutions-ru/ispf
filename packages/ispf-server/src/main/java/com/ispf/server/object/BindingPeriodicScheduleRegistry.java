package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.object.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    static final long DEFAULT_MAX_BACKOFF_MS = 300_000L;

    private final JdbcTemplate jdbcTemplate;
    private final long maxBackoffMs;
    /** Consecutive {@link RuntimeException}s from {@code onPeriodic} per objectPath+ruleId; sets the backoff. */
    private final ConcurrentHashMap<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();

    /** Test / minimal construction without Spring. */
    public BindingPeriodicScheduleRegistry(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_MAX_BACKOFF_MS);
    }

    @Autowired
    public BindingPeriodicScheduleRegistry(
            JdbcTemplate jdbcTemplate,
            @Value("${ispf.binding.periodic.max-backoff-ms:300000}") long maxBackoffMs
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxBackoffMs = Math.max(1L, maxBackoffMs);
    }

    public void syncObject(String objectPath, List<BindingRule> rules) {
        Set<String> periodicRuleIds = new HashSet<>();
        Instant now = Instant.now();
        for (BindingRule rule : rules) {
            if (!isPeriodicRule(rule)) {
                continue;
            }
            periodicRuleIds.add(rule.id());
            // Saving the rules restarts a failing rule now instead of after its backoff.
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
    }

    public void clearAll() {
        jdbcTemplate.update("DELETE FROM platform_binding_periodic_rules");
        consecutiveFailures.clear();
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
                long delayMs = backoffMs(dueRule.periodicMs(), failures);
                log.warn(
                        "Periodic binding {}.{} failed ({} in a row); next attempt in {} ms: {}",
                        dueRule.objectPath(),
                        dueRule.ruleId(),
                        failures,
                        delayMs,
                        ex.getMessage()
                );
                jdbcTemplate.update(
                        """
                                UPDATE platform_binding_periodic_rules
                                SET next_run_at = ?
                                WHERE object_path = ? AND rule_id = ?
                                """,
                        Timestamp.from(now.plusMillis(delayMs)),
                        dueRule.objectPath(),
                        dueRule.ruleId()
                );
                continue;
            }
            Integer failedBefore = consecutiveFailures.remove(key);
            if (failedBefore != null) {
                log.info(
                        "Periodic binding {}.{} recovered after {} failures",
                        dueRule.objectPath(),
                        dueRule.ruleId(),
                        failedBefore
                );
            }
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

    /**
     * Delay before the next attempt after {@code failures} failures in a row: one period, doubling per further
     * failure, never longer than {@code max(periodicMs, maxBackoffMs)}.
     */
    long backoffMs(long periodicMs, int failures) {
        long cap = Math.max(periodicMs, maxBackoffMs);
        long delay = periodicMs;
        for (int i = 1; i < failures && delay < cap; i++) {
            delay = delay > cap / 2 ? cap : delay * 2;
        }
        return delay;
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
        consecutiveFailures.remove(scheduleKey(objectPath, ruleId));
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
