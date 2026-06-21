package com.ispf.server.federation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FederationOutboundAgentStore {

    private final JdbcTemplate jdbcTemplate;

    public FederationOutboundAgentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FederationOutboundAgent insert(FederationOutboundAgentDraft draft) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO federation_outbound_agents (
                    id, name, hub_base_url, registration_code_enc, session_token_enc, path_prefix,
                    enabled, tunnel_status, linked_peer_id, last_error, last_connected_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                draft.name(),
                FederationPeerStore.normalizeBaseUrl(draft.hubBaseUrl()),
                draft.registrationCodeEnc(),
                draft.sessionTokenEnc(),
                draft.pathPrefix() == null ? "root.platform" : draft.pathPrefix().trim(),
                draft.enabled(),
                FederationTunnelStatus.DISCONNECTED.name(),
                draft.linkedPeerId(),
                null,
                null,
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return findById(id).orElseThrow();
    }

    public List<FederationOutboundAgent> listAll() {
        return jdbcTemplate.query("""
                SELECT id, name, hub_base_url, registration_code_enc, session_token_enc, path_prefix,
                       enabled, tunnel_status, linked_peer_id, last_error, last_connected_at, created_at, updated_at
                FROM federation_outbound_agents
                ORDER BY name
                """,
                (rs, rowNum) -> mapRow(rs)
        );
    }

    public List<FederationOutboundAgent> listEnabled() {
        return jdbcTemplate.query("""
                SELECT id, name, hub_base_url, registration_code_enc, session_token_enc, path_prefix,
                       enabled, tunnel_status, linked_peer_id, last_error, last_connected_at, created_at, updated_at
                FROM federation_outbound_agents
                WHERE enabled = TRUE
                ORDER BY name
                """,
                (rs, rowNum) -> mapRow(rs)
        );
    }

    public Optional<FederationOutboundAgent> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, name, hub_base_url, registration_code_enc, session_token_enc, path_prefix,
                       enabled, tunnel_status, linked_peer_id, last_error, last_connected_at, created_at, updated_at
                FROM federation_outbound_agents
                WHERE id = ?
                """,
                (rs, rowNum) -> mapRow(rs),
                id
        ).stream().findFirst();
    }

    public FederationOutboundAgent update(UUID id, FederationOutboundAgentDraft draft) {
        jdbcTemplate.update("""
                UPDATE federation_outbound_agents
                SET name = ?, hub_base_url = ?, registration_code_enc = ?, session_token_enc = ?, path_prefix = ?,
                    enabled = ?, linked_peer_id = ?, updated_at = ?
                WHERE id = ?
                """,
                draft.name(),
                FederationPeerStore.normalizeBaseUrl(draft.hubBaseUrl()),
                draft.registrationCodeEnc(),
                draft.sessionTokenEnc(),
                draft.pathPrefix() == null ? "root.platform" : draft.pathPrefix().trim(),
                draft.enabled(),
                draft.linkedPeerId(),
                Timestamp.from(Instant.now()),
                id
        );
        return findById(id).orElseThrow();
    }

    public void updateStatus(
            UUID id,
            FederationTunnelStatus status,
            UUID linkedPeerId,
            String lastError,
            Instant lastConnectedAt,
            String sessionTokenEnc
    ) {
        jdbcTemplate.update("""
                UPDATE federation_outbound_agents
                SET tunnel_status = ?, linked_peer_id = ?, last_error = ?, last_connected_at = ?,
                    session_token_enc = COALESCE(?, session_token_enc), updated_at = ?
                WHERE id = ?
                """,
                status.name(),
                linkedPeerId,
                lastError,
                lastConnectedAt == null ? null : Timestamp.from(lastConnectedAt),
                sessionTokenEnc,
                Timestamp.from(Instant.now()),
                id
        );
    }

    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM federation_outbound_agents WHERE id = ?", id);
    }

    private static FederationOutboundAgent mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String linkedPeer = rs.getString("linked_peer_id");
        Timestamp lastConnected = rs.getTimestamp("last_connected_at");
        return new FederationOutboundAgent(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("hub_base_url"),
                rs.getString("registration_code_enc"),
                rs.getString("session_token_enc"),
                rs.getString("path_prefix"),
                rs.getBoolean("enabled"),
                FederationTunnelStatus.valueOf(rs.getString("tunnel_status")),
                linkedPeer == null ? null : UUID.fromString(linkedPeer),
                rs.getString("last_error"),
                lastConnected == null ? null : lastConnected.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
