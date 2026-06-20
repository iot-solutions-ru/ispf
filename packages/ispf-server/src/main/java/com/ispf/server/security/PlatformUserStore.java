package com.ispf.server.security;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PlatformUserStore {

    private final JdbcTemplate jdbcTemplate;
    private final String usersTable;

    public PlatformUserStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.usersTable = platformSqlCatalog.table("platform_users");
    }

    public void upsert(PlatformUser user) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE username = ?".formatted(usersTable),
                Integer.class,
                user.username()
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET password_hash = ?, display_name = ?, roles_json = ?, object_path = ?,
                        enabled = ?, updated_at = ?
                    WHERE username = ?
                    """.formatted(usersTable),
                    user.passwordHash(),
                    user.displayName(),
                    user.rolesJson(),
                    user.objectPath(),
                    user.enabled(),
                    Timestamp.from(Instant.now()),
                    user.username()
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO %s (
                    username, password_hash, display_name, roles_json, object_path,
                    enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(usersTable),
                user.username(),
                user.passwordHash(),
                user.displayName(),
                user.rolesJson(),
                user.objectPath(),
                user.enabled(),
                Timestamp.from(user.createdAt()),
                Timestamp.from(Instant.now())
        );
    }

    public void updateProfile(String username, String displayName, String rolesJson, boolean enabled) {
        jdbcTemplate.update("""
                UPDATE %s
                SET display_name = ?, roles_json = ?, enabled = ?, updated_at = ?
                WHERE username = ?
                """.formatted(usersTable),
                displayName,
                rolesJson,
                enabled,
                Timestamp.from(Instant.now()),
                username
        );
    }

    public void updatePassword(String username, String passwordHash) {
        jdbcTemplate.update(
                "UPDATE %s SET password_hash = ?, updated_at = ? WHERE username = ?".formatted(usersTable),
                passwordHash,
                Timestamp.from(Instant.now()),
                username
        );
    }

    public Optional<PlatformUser> findByUsername(String username) {
        List<PlatformUser> rows = jdbcTemplate.query(
                """
                        SELECT username, password_hash, display_name, roles_json, object_path,
                               enabled, created_at, updated_at
                        FROM %s WHERE username = ?
                        """.formatted(usersTable),
                this::mapRow,
                username
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<PlatformUser> listAll() {
        return jdbcTemplate.query(
                """
                        SELECT username, password_hash, display_name, roles_json, object_path,
                               enabled, created_at, updated_at
                        FROM %s ORDER BY username
                        """.formatted(usersTable),
                this::mapRow
        );
    }

    public void delete(String username) {
        jdbcTemplate.update("DELETE FROM %s WHERE username = ?".formatted(usersTable), username);
    }

    public boolean exists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s".formatted(usersTable),
                Integer.class
        );
        return count != null && count > 0;
    }

    private PlatformUser mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new PlatformUser(
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("roles_json"),
                rs.getString("object_path"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    public record PlatformUser(
            String username,
            String passwordHash,
            String displayName,
            String rolesJson,
            String objectPath,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
