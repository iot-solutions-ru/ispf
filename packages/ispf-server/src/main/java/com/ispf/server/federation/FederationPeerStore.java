package com.ispf.server.federation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FederationPeerStore {

    private final JdbcTemplate jdbcTemplate;

    public FederationPeerStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FederationPeer> listAll() {
        return jdbcTemplate.query("""
                SELECT id, name, base_url, auth_token, path_prefix, enabled, description, created_at, updated_at
                FROM federation_peers
                ORDER BY name
                """,
                (rs, rowNum) -> mapRow(rs)
        );
    }

    public Optional<FederationPeer> findById(UUID id) {
        List<FederationPeer> rows = jdbcTemplate.query("""
                SELECT id, name, base_url, auth_token, path_prefix, enabled, description, created_at, updated_at
                FROM federation_peers
                WHERE id = ?
                """,
                (rs, rowNum) -> mapRow(rs),
                id
        );
        return rows.stream().findFirst();
    }

    public FederationPeer insert(FederationPeerDraft draft) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO federation_peers (
                    id, name, base_url, auth_token, path_prefix, enabled, description, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                draft.name(),
                normalizeBaseUrl(draft.baseUrl()),
                blankToNull(draft.authToken()),
                draft.pathPrefix() == null ? "" : draft.pathPrefix().trim(),
                draft.enabled(),
                blankToNull(draft.description()),
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return findById(id).orElseThrow();
    }

    public FederationPeer update(UUID id, FederationPeerDraft draft) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE federation_peers
                SET name = ?, base_url = ?, auth_token = ?, path_prefix = ?, enabled = ?, description = ?, updated_at = ?
                WHERE id = ?
                """,
                draft.name(),
                normalizeBaseUrl(draft.baseUrl()),
                blankToNull(draft.authToken()),
                draft.pathPrefix() == null ? "" : draft.pathPrefix().trim(),
                draft.enabled(),
                blankToNull(draft.description()),
                Timestamp.from(now),
                id
        );
        return findById(id).orElseThrow();
    }

    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM federation_peers WHERE id = ?", id);
    }

    private static FederationPeer mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FederationPeer(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("base_url"),
                rs.getString("auth_token"),
                rs.getString("path_prefix"),
                rs.getBoolean("enabled"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
