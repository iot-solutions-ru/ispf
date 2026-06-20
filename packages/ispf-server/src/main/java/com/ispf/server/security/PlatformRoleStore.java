package com.ispf.server.security;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PlatformRoleStore {

    private final JdbcTemplate jdbcTemplate;
    private final String rolesTable;

    private static final RowMapper<PlatformRole> ROW_MAPPER = (rs, rowNum) -> new PlatformRole(
            rs.getString("name"),
            rs.getString("display_name"),
            rs.getString("description"),
            rs.getBoolean("built_in"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    public PlatformRoleStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.rolesTable = platformSqlCatalog.table("platform_roles");
    }

    public boolean exists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s".formatted(rolesTable),
                Integer.class
        );
        return count != null && count > 0;
    }

    public List<PlatformRole> listAll() {
        return jdbcTemplate.query(
                "SELECT * FROM %s ORDER BY name".formatted(rolesTable),
                ROW_MAPPER
        );
    }

    public Optional<PlatformRole> findByName(String name) {
        List<PlatformRole> rows = jdbcTemplate.query(
                "SELECT * FROM %s WHERE name = ?".formatted(rolesTable),
                ROW_MAPPER,
                name
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void upsert(PlatformRole role) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE name = ?".formatted(rolesTable),
                Integer.class,
                role.name()
        );
        Instant now = Instant.now();
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET display_name = ?, description = ?, built_in = ?, updated_at = ?
                    WHERE name = ?
                    """.formatted(rolesTable),
                    role.displayName(),
                    role.description(),
                    role.builtIn(),
                    Timestamp.from(now),
                    role.name()
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO %s (name, display_name, description, built_in, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(rolesTable),
                role.name(),
                role.displayName(),
                role.description(),
                role.builtIn(),
                Timestamp.from(role.createdAt()),
                Timestamp.from(now)
        );
    }

    public void updateProfile(String name, String displayName, String description) {
        jdbcTemplate.update("""
                UPDATE %s SET display_name = ?, description = ?, updated_at = ? WHERE name = ?
                """.formatted(rolesTable),
                displayName,
                description,
                Timestamp.from(Instant.now()),
                name
        );
    }

    public void delete(String name) {
        jdbcTemplate.update("DELETE FROM %s WHERE name = ?".formatted(rolesTable), name);
    }

    public record PlatformRole(
            String name,
            String displayName,
            String description,
            boolean builtIn,
            Instant createdAt,
            Instant updatedAt
    ) {
        public String objectPath() {
            return PlatformUserService.ROLES_FOLDER + "." + name;
        }
    }
}
