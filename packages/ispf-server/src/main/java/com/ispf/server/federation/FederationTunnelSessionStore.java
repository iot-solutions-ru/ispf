package com.ispf.server.federation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FederationTunnelSessionStore {

    private final JdbcTemplate jdbcTemplate;

    public FederationTunnelSessionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(
            String sessionId,
            UUID registrationId,
            UUID peerId,
            String agentVersion
    ) {
        Instant now = Instant.now();
        jdbcTemplate.update("DELETE FROM federation_tunnel_sessions WHERE session_id = ?", sessionId);
        jdbcTemplate.update("""
                INSERT INTO federation_tunnel_sessions (
                    session_id, registration_id, peer_id, connected_at, last_ping_at, agent_version, disconnected_at
                ) VALUES (?, ?, ?, ?, ?, ?, NULL)
                """,
                sessionId,
                registrationId,
                peerId,
                Timestamp.from(now),
                Timestamp.from(now),
                agentVersion
        );
    }

    public void touchPing(String sessionId) {
        jdbcTemplate.update(
                "UPDATE federation_tunnel_sessions SET last_ping_at = ? WHERE session_id = ? AND disconnected_at IS NULL",
                Timestamp.from(Instant.now()),
                sessionId
        );
    }

    public void disconnect(String sessionId) {
        jdbcTemplate.update(
                "UPDATE federation_tunnel_sessions SET disconnected_at = ? WHERE session_id = ?",
                Timestamp.from(Instant.now()),
                sessionId
        );
    }

    public void disconnectByPeer(UUID peerId) {
        jdbcTemplate.update(
                "UPDATE federation_tunnel_sessions SET disconnected_at = ? WHERE peer_id = ? AND disconnected_at IS NULL",
                Timestamp.from(Instant.now()),
                peerId
        );
    }

    public List<FederationTunnelSession> listActive() {
        return jdbcTemplate.query("""
                SELECT session_id, registration_id, peer_id, connected_at, last_ping_at, agent_version, disconnected_at
                FROM federation_tunnel_sessions
                WHERE disconnected_at IS NULL
                ORDER BY connected_at DESC
                """,
                (rs, rowNum) -> mapRow(rs)
        );
    }

    public Optional<FederationTunnelSession> findActiveByPeerId(UUID peerId) {
        return jdbcTemplate.query("""
                SELECT session_id, registration_id, peer_id, connected_at, last_ping_at, agent_version, disconnected_at
                FROM federation_tunnel_sessions
                WHERE peer_id = ? AND disconnected_at IS NULL
                ORDER BY connected_at DESC
                LIMIT 1
                """,
                (rs, rowNum) -> mapRow(rs),
                peerId
        ).stream().findFirst();
    }

    private static FederationTunnelSession mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String registrationId = rs.getString("registration_id");
        Timestamp disconnected = rs.getTimestamp("disconnected_at");
        return new FederationTunnelSession(
                rs.getString("session_id"),
                registrationId == null ? null : UUID.fromString(registrationId),
                UUID.fromString(rs.getString("peer_id")),
                rs.getTimestamp("connected_at").toInstant(),
                rs.getTimestamp("last_ping_at").toInstant(),
                rs.getString("agent_version"),
                disconnected == null ? null : disconnected.toInstant()
        );
    }

    public record FederationTunnelSession(
            String sessionId,
            UUID registrationId,
            UUID peerId,
            Instant connectedAt,
            Instant lastPingAt,
            String agentVersion,
            Instant disconnectedAt
    ) {
    }
}
