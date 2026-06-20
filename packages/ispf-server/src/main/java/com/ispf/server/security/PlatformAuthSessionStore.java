package com.ispf.server.security;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PlatformAuthSessionStore {

    private final JdbcTemplate jdbcTemplate;
    private final String sessionsTable;

    public PlatformAuthSessionStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionsTable = platformSqlCatalog.table("platform_auth_sessions");
    }

    public void save(String token, String username, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO %s (token, username, expires_at, created_at)
                VALUES (?, ?, ?, ?)
                """.formatted(sessionsTable),
                token,
                username,
                Timestamp.from(expiresAt),
                Timestamp.from(Instant.now())
        );
    }

    public Optional<AuthSession> findValid(String token, Instant now) {
        List<AuthSession> rows = jdbcTemplate.query(
                """
                        SELECT token, username, expires_at
                        FROM %s
                        WHERE token = ? AND expires_at > ?
                        """.formatted(sessionsTable),
                (rs, rowNum) -> new AuthSession(
                        rs.getString("token"),
                        rs.getString("username"),
                        rs.getTimestamp("expires_at").toInstant()
                ),
                token,
                Timestamp.from(now)
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void delete(String token) {
        jdbcTemplate.update("DELETE FROM %s WHERE token = ?".formatted(sessionsTable), token);
    }

    public void deleteExpired(Instant now) {
        jdbcTemplate.update(
                "DELETE FROM %s WHERE expires_at <= ?".formatted(sessionsTable),
                Timestamp.from(now)
        );
    }

    public int countValidSessions(Instant now) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE expires_at > ?".formatted(sessionsTable),
                Integer.class,
                Timestamp.from(now)
        );
        return count != null ? count : 0;
    }

    public record AuthSession(String token, String username, Instant expiresAt) {
    }
}
