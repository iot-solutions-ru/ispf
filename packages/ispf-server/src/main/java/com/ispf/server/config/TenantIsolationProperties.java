package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Multi-tenant isolation mode — BL-155.
 * {@code logical}: shared DB schema, path-based namespaces (default).
 * {@code hard}: per-tenant PostgreSQL schema (stub — schema provisioning is follow-up work).
 */
@ConfigurationProperties(prefix = "ispf.tenant")
public class TenantIsolationProperties {

    public enum IsolationMode {
        LOGICAL,
        HARD
    }

    /**
     * {@code logical} — object-path namespaces under {@code root.tenant.*}.
     * {@code hard} — reserved PostgreSQL schema per tenant ({@link #schemaPrefix}{@code tenantId}).
     */
    private IsolationMode isolationMode = IsolationMode.LOGICAL;

    /** Prefix for per-tenant schema names when {@link #isolationMode} is {@code hard}. */
    private String schemaPrefix = "tenant_";

    public IsolationMode getIsolationMode() {
        return isolationMode;
    }

    public void setIsolationMode(IsolationMode isolationMode) {
        this.isolationMode = isolationMode != null ? isolationMode : IsolationMode.LOGICAL;
    }

    public String getSchemaPrefix() {
        return schemaPrefix;
    }

    public void setSchemaPrefix(String schemaPrefix) {
        this.schemaPrefix = schemaPrefix != null && !schemaPrefix.isBlank() ? schemaPrefix : "tenant_";
    }

    public boolean isHardMode() {
        return isolationMode == IsolationMode.HARD;
    }

    public String schemaNameForTenant(String tenantId) {
        return schemaPrefix + tenantId;
    }
}
