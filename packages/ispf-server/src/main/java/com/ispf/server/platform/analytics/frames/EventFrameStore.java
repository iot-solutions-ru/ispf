package com.ispf.server.platform.analytics.frames;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EventFrameStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;

    public EventFrameStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(EventFrame frame) {
        jdbcTemplate.update("""
                        INSERT INTO platform_event_frames (
                            frame_id, frame_type, scope_path, source_path, source_key, label,
                            started_at, ended_at, downtime_minutes, metadata_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                frame.frameId(),
                frame.frameType().externalName(),
                frame.scopePath(),
                frame.sourcePath(),
                frame.sourceKey(),
                frame.label(),
                Timestamp.from(frame.startedAt()),
                frame.endedAt() != null ? Timestamp.from(frame.endedAt()) : null,
                frame.downtimeMinutes(),
                serializeMetadata(frame.metadata())
        );
    }

    void close(UUID frameId, Instant endedAt, int downtimeMinutes) {
        jdbcTemplate.update("""
                        UPDATE platform_event_frames
                        SET ended_at = ?, downtime_minutes = ?
                        WHERE frame_id = ?
                        """,
                Timestamp.from(endedAt),
                downtimeMinutes,
                frameId
        );
    }

    void updateDowntime(UUID frameId, int downtimeMinutes) {
        jdbcTemplate.update(
                "UPDATE platform_event_frames SET downtime_minutes = ? WHERE frame_id = ?",
                downtimeMinutes,
                frameId
        );
    }

    Optional<EventFrame> find(UUID frameId) {
        List<EventFrame> rows = jdbcTemplate.query(
                "SELECT * FROM platform_event_frames WHERE frame_id = ?",
                mapper(),
                frameId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    List<EventFrame> listActive(String scopePath) {
        if (scopePath == null || scopePath.isBlank()) {
            return jdbcTemplate.query(
                    "SELECT * FROM platform_event_frames WHERE ended_at IS NULL ORDER BY started_at DESC",
                    mapper()
            );
        }
        return jdbcTemplate.query(
                """
                        SELECT * FROM platform_event_frames
                        WHERE scope_path = ? AND ended_at IS NULL
                        ORDER BY started_at DESC
                        """,
                mapper(),
                scopePath
        );
    }

    Optional<EventFrame> findActive(String scopePath, EventFrameType type) {
        List<EventFrame> rows = jdbcTemplate.query(
                """
                        SELECT * FROM platform_event_frames
                        WHERE scope_path = ? AND frame_type = ? AND ended_at IS NULL
                        ORDER BY started_at DESC
                        LIMIT 1
                        """,
                mapper(),
                scopePath,
                type.externalName()
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    List<EventFrame> listForScope(String scopePath, Instant from, Instant to) {
        return jdbcTemplate.query(
                """
                        SELECT * FROM platform_event_frames
                        WHERE scope_path = ?
                          AND started_at < ?
                          AND (ended_at IS NULL OR ended_at > ?)
                        ORDER BY started_at DESC
                        """,
                mapper(),
                scopePath,
                Timestamp.from(to),
                Timestamp.from(from)
        );
    }

    List<EventFrame> loadActiveOnStartup() {
        return jdbcTemplate.query(
                "SELECT * FROM platform_event_frames WHERE ended_at IS NULL ORDER BY started_at",
                mapper()
        );
    }

    private RowMapper<EventFrame> mapper() {
        return (rs, rowNum) -> new EventFrame(
                UUID.fromString(rs.getString("frame_id")),
                EventFrameType.parse(rs.getString("frame_type")),
                rs.getString("scope_path"),
                rs.getString("source_path"),
                rs.getString("source_key"),
                rs.getString("label"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("ended_at") != null ? rs.getTimestamp("ended_at").toInstant() : null,
                rs.getInt("downtime_minutes"),
                deserializeMetadata(rs.getString("metadata_json"))
        );
    }

    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize event frame metadata", ex);
        }
    }

    private Map<String, String> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, METADATA_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
