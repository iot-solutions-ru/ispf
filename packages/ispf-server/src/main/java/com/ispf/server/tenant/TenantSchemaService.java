package com.ispf.server.tenant;

import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.config.TenantIsolationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Provisions / drops per-tenant PostgreSQL schemas when {@code ispf.tenant.isolation-mode=hard} — BL-155.
 * App-data and marketplace schemas can use {@link ApplicationSchemaSession#runInSchema}; platform object
 * tables remain shared until a dedicated routing cutover.
 */
@Service
public class TenantSchemaService {

    private final TenantIsolationProperties properties;
    private final TenantIsolationValidator isolationValidator;
    private final ApplicationSchemaSession schemaSession;
    private final JdbcTemplate jdbcTemplate;

    public TenantSchemaService(
            TenantIsolationProperties properties,
            TenantIsolationValidator isolationValidator,
            ApplicationSchemaSession schemaSession,
            JdbcTemplate jdbcTemplate
    ) {
        this.properties = properties;
        this.isolationValidator = isolationValidator;
        this.schemaSession = schemaSession;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates {@code tenant_{id}} schema when hard isolation is enabled; no-op in logical mode.
     */
    public String provisionTenantSchema(String tenantId) {
        if (!properties.isHardMode()) {
            return null;
        }
        String schemaName = isolationValidator.resolveSchemaName(tenantId);
        schemaSession.ensureSchemaExists(schemaName);
        isolationValidator.assertSchemaProvisioned(tenantId);
        return schemaName;
    }

    /**
     * Drops the tenant schema (CASCADE) when hard isolation is enabled.
     */
    public void dropTenantSchema(String tenantId) {
        if (!properties.isHardMode() || tenantId == null || tenantId.isBlank()) {
            return;
        }
        String schemaName = isolationValidator.resolveSchemaName(tenantId);
        String quoted = ApplicationSchemaSupport.quoteIdentifier(schemaName);
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + quoted + " CASCADE");
    }

    /**
     * Runs {@code action} with {@code search_path} set to the tenant schema (hard mode only).
     */
    public void runInTenantSchema(String tenantId, Runnable action) {
        if (!properties.isHardMode()) {
            action.run();
            return;
        }
        String schemaName = isolationValidator.resolveSchemaName(tenantId);
        isolationValidator.assertSchemaProvisioned(tenantId);
        schemaSession.runInSchema(schemaName, action);
    }
}
