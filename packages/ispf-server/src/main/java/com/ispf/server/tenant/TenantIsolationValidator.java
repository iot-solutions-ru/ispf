package com.ispf.server.tenant;

import com.ispf.server.config.TenantIsolationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hard-mode tenant isolation rules (BL-155): schema naming, reserved names, existence checks.
 * Does not route platform object tables into tenant schemas — shared-table A≠B uses PostgreSQL RLS
 * ({@code ispf.tenant.db-row-isolation}); physical schema split remains optional.
 */
@Component
public class TenantIsolationValidator {

    private static final Pattern PG_SCHEMA_SUFFIX = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Set<String> RESERVED_SCHEMA_NAMES = Set.of(
            "public",
            "information_schema",
            "pg_catalog",
            "pg_toast",
            "pg_temp",
            "pg_toast_temp",
            "ispf",
            "flyway"
    );

    private final TenantIsolationProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public TenantIsolationValidator(TenantIsolationProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void validateTenantIdForCreate(String tenantId) {
        if (!properties.isHardMode()) {
            return;
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hard mode tenantId is required");
        }
        if (!PG_SCHEMA_SUFFIX.matcher(tenantId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hard mode tenantId must match [a-z][a-z0-9_]{0,62} for schema prefix "
                            + properties.getSchemaPrefix()
            );
        }
        String schemaName = properties.schemaNameForTenant(tenantId);
        if (schemaName.length() > 63) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hard mode schema name exceeds 63 characters: " + schemaName
            );
        }
        rejectReservedSchema(schemaName);
        if (schemaExists(schemaName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hard mode schema already exists: " + schemaName
            );
        }
    }

    /**
     * After provision: schema must exist when hard mode is on.
     */
    public void assertSchemaProvisioned(String tenantId) {
        if (!properties.isHardMode()) {
            return;
        }
        String schemaName = resolveSchemaName(tenantId);
        if (!schemaExists(schemaName)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Hard mode schema was not provisioned: " + schemaName
            );
        }
    }

    public void rejectReservedSchema(String schemaName) {
        String normalized = schemaName == null ? "" : schemaName.toLowerCase(Locale.ROOT);
        if (RESERVED_SCHEMA_NAMES.contains(normalized)
                || normalized.startsWith("pg_")
                || normalized.startsWith("flyway_")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hard mode schema name is reserved: " + schemaName
            );
        }
    }

    public boolean schemaExists(String schemaName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?",
                Integer.class,
                schemaName
        );
        return count != null && count > 0;
    }

    public String resolveSchemaName(String tenantId) {
        String schemaName = properties.schemaNameForTenant(tenantId);
        rejectReservedSchema(schemaName);
        return schemaName;
    }

    /**
     * Maps OIDC / JWT tenant claim values to an ISPF tenant id when hard or logical isolation is used.
     * Claim may be the bare tenant id or a namespaced value ({@code tenant:&lt;id&gt;}, {@code ispf:&lt;id&gt;}).
     */
    public String normalizeOidcTenantClaim(String claimValue) {
        if (claimValue == null || claimValue.isBlank()) {
            return null;
        }
        String trimmed = claimValue.trim();
        int colon = trimmed.lastIndexOf(':');
        String candidate = colon >= 0 ? trimmed.substring(colon + 1).trim() : trimmed;
        if (candidate.isBlank()) {
            return null;
        }
        if (!PG_SCHEMA_SUFFIX.matcher(candidate).matches()
                && !Pattern.compile("^[a-z][a-z0-9-]{1,62}$").matcher(candidate).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OIDC tenant claim is not a valid tenant id: " + claimValue
            );
        }
        return candidate;
    }
}
