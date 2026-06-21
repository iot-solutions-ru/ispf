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

    private static final String SELECT_COLUMNS = """
            id, name, base_url, auth_token, path_prefix, enabled, description,
            connection_mode, auth_mode, token_expires_at, auth_username, auth_secret_enc,
            auth_status, last_auth_at, last_auth_error, created_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public FederationPeerStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FederationPeer> listAll() {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM federation_peers ORDER BY name",
                (rs, rowNum) -> mapRow(rs)
        );
    }

    public List<FederationPeer> listServiceAccountPeers() {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + """
                         FROM federation_peers
                        WHERE auth_mode = 'SERVICE_ACCOUNT'
                          AND enabled = TRUE
                          AND connection_mode = 'HTTP_PULL'
                        ORDER BY name
                        """,
                (rs, rowNum) -> mapRow(rs)
        );
    }

    public Optional<FederationPeer> findById(UUID id) {
        List<FederationPeer> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM federation_peers WHERE id = ?",
                (rs, rowNum) -> mapRow(rs),
                id
        );
        return rows.stream().findFirst();
    }

    public Optional<FederationPeer> findByName(String name) {
        List<FederationPeer> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM federation_peers WHERE name = ?",
                (rs, rowNum) -> mapRow(rs),
                name
        );
        return rows.stream().findFirst();
    }

    public FederationPeer insert(FederationPeerDraft draft) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO federation_peers (
                    id, name, base_url, auth_token, path_prefix, enabled, description,
                    connection_mode, auth_mode, token_expires_at, auth_username, auth_secret_enc,
                    auth_status, last_auth_at, last_auth_error, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                draft.name(),
                normalizeBaseUrl(draft.baseUrl(), draft.connectionMode()),
                blankToNull(draft.authToken()),
                draft.pathPrefix() == null ? "" : draft.pathPrefix().trim(),
                draft.enabled(),
                blankToNull(draft.description()),
                (draft.connectionMode() != null ? draft.connectionMode() : FederationConnectionMode.HTTP_PULL).name(),
                (draft.authMode() != null ? draft.authMode() : FederationAuthMode.STATIC_TOKEN).name(),
                draft.tokenExpiresAt() == null ? null : Timestamp.from(draft.tokenExpiresAt()),
                blankToNull(draft.authUsername()),
                blankToNull(draft.authSecretEnc()),
                (draft.authStatus() != null ? draft.authStatus() : FederationAuthStatus.OK).name(),
                draft.lastAuthAt() == null ? null : Timestamp.from(draft.lastAuthAt()),
                blankToNull(draft.lastAuthError()),
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return findById(id).orElseThrow();
    }

    public FederationPeer update(UUID id, FederationPeerDraft draft) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE federation_peers
                SET name = ?, base_url = ?, auth_token = ?, path_prefix = ?, enabled = ?, description = ?,
                    connection_mode = ?, auth_mode = ?, token_expires_at = ?, auth_username = ?, auth_secret_enc = ?,
                    auth_status = ?, last_auth_at = ?, last_auth_error = ?, updated_at = ?
                WHERE id = ?
                """,
                draft.name(),
                normalizeBaseUrl(draft.baseUrl(), draft.connectionMode()),
                blankToNull(draft.authToken()),
                draft.pathPrefix() == null ? "" : draft.pathPrefix().trim(),
                draft.enabled(),
                blankToNull(draft.description()),
                (draft.connectionMode() != null ? draft.connectionMode() : FederationConnectionMode.HTTP_PULL).name(),
                (draft.authMode() != null ? draft.authMode() : FederationAuthMode.STATIC_TOKEN).name(),
                draft.tokenExpiresAt() == null ? null : Timestamp.from(draft.tokenExpiresAt()),
                blankToNull(draft.authUsername()),
                blankToNull(draft.authSecretEnc()),
                (draft.authStatus() != null ? draft.authStatus() : FederationAuthStatus.OK).name(),
                draft.lastAuthAt() == null ? null : Timestamp.from(draft.lastAuthAt()),
                blankToNull(draft.lastAuthError()),
                Timestamp.from(now),
                id
        );
        return findById(id).orElseThrow();
    }

    public void updateAuthState(
            UUID id,
            String authToken,
            Instant tokenExpiresAt,
            FederationAuthStatus authStatus,
            Instant lastAuthAt,
            String lastAuthError
    ) {
        jdbcTemplate.update("""
                UPDATE federation_peers
                SET auth_token = ?, token_expires_at = ?, auth_status = ?,
                    last_auth_at = ?, last_auth_error = ?, updated_at = ?
                WHERE id = ?
                """,
                blankToNull(authToken),
                tokenExpiresAt == null ? null : Timestamp.from(tokenExpiresAt),
                authStatus.name(),
                lastAuthAt == null ? null : Timestamp.from(lastAuthAt),
                blankToNull(lastAuthError),
                Timestamp.from(Instant.now()),
                id
        );
    }

    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM federation_peers WHERE id = ?", id);
    }

    static String tunnelBaseUrl(String peerName) {
        return "tunnel://" + FederationPaths.slug(peerName);
    }

    static String normalizeBaseUrl(String baseUrl, FederationConnectionMode mode) {
        if (mode == FederationConnectionMode.TUNNEL_INBOUND) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl is required");
            }
            return baseUrl.trim();
        }
        return normalizeBaseUrl(baseUrl);
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

    private static FederationPeer mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp tokenExpires = rs.getTimestamp("token_expires_at");
        Timestamp lastAuth = rs.getTimestamp("last_auth_at");
        return new FederationPeer(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("base_url"),
                rs.getString("auth_token"),
                rs.getString("path_prefix"),
                rs.getBoolean("enabled"),
                rs.getString("description"),
                FederationConnectionMode.valueOf(rs.getString("connection_mode")),
                FederationAuthMode.valueOf(rs.getString("auth_mode")),
                tokenExpires == null ? null : tokenExpires.toInstant(),
                rs.getString("auth_username"),
                rs.getString("auth_secret_enc"),
                FederationAuthStatus.valueOf(rs.getString("auth_status")),
                lastAuth == null ? null : lastAuth.toInstant(),
                rs.getString("last_auth_error"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
