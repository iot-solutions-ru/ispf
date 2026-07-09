package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JDBC index of analytics tag periodic schedules (BL-203).
 */
@Service
public class AnalyticsScheduleRegistry {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AnalyticsScheduleRegistry(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void syncAll(List<AnalyticsTagDefinition> tags) {
        Set<String> activePaths = new HashSet<>();
        Instant now = Instant.now();
        for (AnalyticsTagDefinition tag : tags) {
            activePaths.add(tag.tagPath());
            upsert(tag, now);
        }
        if (activePaths.isEmpty()) {
            jdbcTemplate.update("DELETE FROM platform_analytics_schedules");
            return;
        }
        jdbcTemplate.update(
                """
                        DELETE FROM platform_analytics_schedules
                        WHERE tag_path NOT IN (%s)
                        """.formatted(placeholders(activePaths.size())),
                activePaths.toArray()
        );
    }

    public int countEnabled() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM platform_analytics_schedules
                        WHERE enabled = TRUE AND periodic_ms > 0
                        """,
                Integer.class
        );
        return count != null ? count : 0;
    }

    public Instant nextWakeAt() {
        return jdbcTemplate.query(
                """
                        SELECT MIN(next_run_at) FROM platform_analytics_schedules
                        WHERE enabled = TRUE AND periodic_ms > 0
                        """,
                rs -> rs.next() ? toInstant(rs.getTimestamp(1)) : null
        );
    }

    public List<String> dueTagPaths(Instant now) {
        return jdbcTemplate.queryForList(
                """
                        SELECT tag_path FROM platform_analytics_schedules
                        WHERE enabled = TRUE
                          AND periodic_ms > 0
                          AND next_run_at <= ?
                        ORDER BY next_run_at, tag_path
                        """,
                String.class,
                Timestamp.from(now)
        );
    }

    public void markRan(String tagPath, long periodicMs, Instant now, String error) {
        Instant next = now.plusMillis(Math.max(periodicMs, 1L));
        jdbcTemplate.update(
                """
                        UPDATE platform_analytics_schedules
                        SET last_tick_at = ?, next_run_at = ?, last_error = ?
                        WHERE tag_path = ?
                        """,
                Timestamp.from(now),
                Timestamp.from(next),
                error,
                tagPath
        );
    }

    public Instant lastTickAt(String tagPath) {
        return jdbcTemplate.query(
                """
                        SELECT last_tick_at FROM platform_analytics_schedules
                        WHERE tag_path = ?
                        """,
                rs -> rs.next() ? toInstant(rs.getTimestamp(1)) : null,
                tagPath
        );
    }

    private void upsert(AnalyticsTagDefinition tag, Instant now) {
        String sourcesJson;
        try {
            sourcesJson = objectMapper.writeValueAsString(tag.sources().stream()
                    .map(ref -> new AnalyticsSourceRef(ref.path(), ref.variable(), ref.field()))
                    .toList());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize analytics sources", ex);
        }
        int updated = jdbcTemplate.update(
                """
                        UPDATE platform_analytics_schedules
                        SET helper = ?, source_paths_json = ?, window_bucket = ?,
                            periodic_ms = ?, on_change_enabled = ?, enabled = ?, next_run_at = ?
                        WHERE tag_path = ?
                        """,
                tag.helper(),
                sourcesJson,
                tag.windowBucket(),
                tag.periodicMs(),
                tag.onChangeEnabled(),
                tag.enabled(),
                Timestamp.from(now),
                tag.tagPath()
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO platform_analytics_schedules (
                                tag_path, helper, source_paths_json, window_bucket,
                                periodic_ms, on_change_enabled, enabled, next_run_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    tag.tagPath(),
                    tag.helper(),
                    sourcesJson,
                    tag.windowBucket(),
                    tag.periodicMs(),
                    tag.onChangeEnabled(),
                    tag.enabled(),
                    Timestamp.from(now)
            );
        }
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
