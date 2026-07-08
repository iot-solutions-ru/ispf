package com.ispf.server.tenant;

import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.config.TenantIsolationProperties;
import org.springframework.stereotype.Service;

/**
 * Provisions per-tenant PostgreSQL schemas when {@code ispf.tenant.isolation-mode=hard} — BL-155.
 */
@Service
public class TenantSchemaService {

    private final TenantIsolationProperties properties;
    private final TenantIsolationValidator isolationValidator;
    private final ApplicationSchemaSession schemaSession;

    public TenantSchemaService(
            TenantIsolationProperties properties,
            TenantIsolationValidator isolationValidator,
            ApplicationSchemaSession schemaSession
    ) {
        this.properties = properties;
        this.isolationValidator = isolationValidator;
        this.schemaSession = schemaSession;
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
        return schemaName;
    }
}
