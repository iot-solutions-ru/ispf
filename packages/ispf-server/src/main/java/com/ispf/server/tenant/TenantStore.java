package com.ispf.server.tenant;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class TenantStore {

    private final JdbcTemplate jdbcTemplate;
    private final String tenantsTable;
    private final String usersTable;

    public TenantStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantsTable = platformSqlCatalog.table("platform_tenants");
        this.usersTable = platformSqlCatalog.table("platform_users");
    }

    public List<Tenant> listAll() {
        return jdbcTemplate.query("""
                SELECT tenant_id, display_name, enabled, created_at, updated_at
                FROM %s
                ORDER BY tenant_id
                """.formatted(tenantsTable),
                (rs, rowNum) -> mapRow(rs)
        );
    }

    public Optional<Tenant> findById(String tenantId) {
        List<Tenant> rows = jdbcTemplate.query("""
                SELECT tenant_id, display_name, enabled, created_at, updated_at
                FROM %s
                WHERE tenant_id = ?
                """.formatted(tenantsTable),
                (rs, rowNum) -> mapRow(rs),
                tenantId
        );
        return rows.stream().findFirst();
    }

    public Tenant insert(TenantDraft draft) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO %s (tenant_id, display_name, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(tenantsTable),
                draft.tenantId(),
                draft.displayName(),
                draft.enabled(),
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return findById(draft.tenantId()).orElseThrow();
    }

    public void delete(String tenantId) {
        jdbcTemplate.update("DELETE FROM %s WHERE tenant_id = ?".formatted(tenantsTable), tenantId);
    }

    public Optional<String> findTenantIdForUser(String username) {
        List<String> rows = jdbcTemplate.query(
                "SELECT tenant_id FROM %s WHERE username = ?".formatted(usersTable),
                (rs, rowNum) -> rs.getString("tenant_id"),
                username
        );
        return rows.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public void assignUserTenant(String username, String tenantId) {
        int updated = jdbcTemplate.update(
                "UPDATE %s SET tenant_id = ?, updated_at = ? WHERE username = ?".formatted(usersTable),
                tenantId,
                Timestamp.from(Instant.now()),
                username
        );
        if (updated == 0) {
            throw new IllegalArgumentException("User not found: " + username);
        }
    }

    public void clearUserTenant(String username) {
        jdbcTemplate.update(
                "UPDATE %s SET tenant_id = NULL, updated_at = ? WHERE username = ?".formatted(usersTable),
                Timestamp.from(Instant.now()),
                username
        );
    }

    private static Tenant mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Tenant(
                rs.getString("tenant_id"),
                rs.getString("display_name"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
