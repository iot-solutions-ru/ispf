package com.ispf.server.federation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FederationInboundRegistrationStore {

    private final JdbcTemplate jdbcTemplate;

    public FederationInboundRegistrationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FederationInboundRegistration insert(
            String name,
            String codeHash,
            String pathPrefix,
            Instant expiresAt,
            String createdBy
    ) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO federation_inbound_registrations (
                    id, name, registration_code_hash, path_prefix, expires_at, consumed_at, created_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?)
                """,
                id,
                name,
                codeHash,
                pathPrefix == null ? "root.platform" : pathPrefix.trim(),
                Timestamp.from(expiresAt),
                createdBy,
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return findById(id).orElseThrow();
    }

    public List<FederationInboundRegistration> listAll() {
        return jdbcTemplate.query("""
                SELECT id, name, registration_code_hash, path_prefix, expires_at, consumed_at, created_by, created_at, updated_at
                FROM federation_inbound_registrations
                ORDER BY created_at DESC
                """,
                (rs, rowNum) -> mapRow(rs)
        );
    }

    public Optional<FederationInboundRegistration> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, name, registration_code_hash, path_prefix, expires_at, consumed_at, created_by, created_at, updated_at
                FROM federation_inbound_registrations
                WHERE id = ?
                """,
                (rs, rowNum) -> mapRow(rs),
                id
        ).stream().findFirst();
    }

    public Optional<FederationInboundRegistration> findValidByCodeHash(String codeHash, Instant now) {
        return jdbcTemplate.query("""
                SELECT id, name, registration_code_hash, path_prefix, expires_at, consumed_at, created_by, created_at, updated_at
                FROM federation_inbound_registrations
                WHERE registration_code_hash = ?
                  AND consumed_at IS NULL
                  AND expires_at > ?
                """,
                (rs, rowNum) -> mapRow(rs),
                codeHash,
                Timestamp.from(now)
        ).stream().findFirst();
    }

    public void markConsumed(UUID id, Instant consumedAt) {
        jdbcTemplate.update("""
                UPDATE federation_inbound_registrations
                SET consumed_at = ?, updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(consumedAt),
                Timestamp.from(Instant.now()),
                id
        );
    }

    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM federation_inbound_registrations WHERE id = ?", id);
    }

    private static FederationInboundRegistration mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp consumed = rs.getTimestamp("consumed_at");
        return new FederationInboundRegistration(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("registration_code_hash"),
                rs.getString("path_prefix"),
                rs.getTimestamp("expires_at").toInstant(),
                consumed == null ? null : consumed.toInstant(),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    public record FederationInboundRegistration(
            UUID id,
            String name,
            String registrationCodeHash,
            String pathPrefix,
            Instant expiresAt,
            Instant consumedAt,
            String createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
