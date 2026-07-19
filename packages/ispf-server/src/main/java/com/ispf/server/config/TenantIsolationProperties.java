package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Multi-tenant isolation mode — BL-155.
 * {@code logical}: shared DB schema, path-based namespaces (default).
 * {@code hard}: per-tenant PostgreSQL schema ({@link com.ispf.server.tenant.TenantSchemaService}).
 * {@code dbRowIsolation}: PostgreSQL RLS session GUCs on shared object tables.
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

    /**
     * JWT / OIDC claim carrying the ISPF tenant id (BL-155). Empty disables claim mapping.
     * Common values: {@code tenant_id}, {@code ispf_tenant}, {@code tid}.
     */
    private String oidcTenantClaim = "tenant_id";

    /**
     * When true (default), apply PostgreSQL RLS session GUCs ({@code app.tenant_id} /
     * {@code app.tenant_bypass}) on connection checkout. No-op on H2 / non-PostgreSQL.
     */
    private boolean dbRowIsolation = true;

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

    public String getOidcTenantClaim() {
        return oidcTenantClaim;
    }

    public void setOidcTenantClaim(String oidcTenantClaim) {
        this.oidcTenantClaim = oidcTenantClaim != null ? oidcTenantClaim.trim() : "";
    }

    public boolean isDbRowIsolation() {
        return dbRowIsolation;
    }

    public void setDbRowIsolation(boolean dbRowIsolation) {
        this.dbRowIsolation = dbRowIsolation;
    }

    public boolean isHardMode() {
        return isolationMode == IsolationMode.HARD;
    }

    public String schemaNameForTenant(String tenantId) {
        return schemaPrefix + tenantId;
    }
}
