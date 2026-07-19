package com.ispf.server.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * Applies / clears PostgreSQL RLS GUC session variables on a connection (BL-155).
 * Uses {@code set_config(..., false)} for session-level (not transaction-LOCAL) scope.
 */
public final class TenantRlsSession {

    public static final String GUC_BYPASS = "app.tenant_bypass";
    public static final String GUC_TENANT_ID = "app.tenant_id";

    /** Same pattern as {@link TenantService} tenant ids. */
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,62}$");

    private TenantRlsSession() {
    }

    public static void apply(Connection connection, TenantRlsContext.State state) throws SQLException {
        boolean bypassOn = state == null || state.bypass();
        String tenantId = "";
        if (!bypassOn && state != null) {
            tenantId = sanitizeTenantId(state.tenantId());
            if (tenantId.isEmpty()) {
                // Invalid / empty tenant with bypass off would deny all rows; treat as bypass.
                bypassOn = true;
            }
        }
        setConfig(connection, GUC_BYPASS, bypassOn ? "on" : "off");
        setConfig(connection, GUC_TENANT_ID, tenantId);
    }

    /** Reset pooled connection to default-allow before return to pool. */
    public static void clear(Connection connection) throws SQLException {
        setConfig(connection, GUC_BYPASS, "on");
        setConfig(connection, GUC_TENANT_ID, "");
    }

    static String sanitizeTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }
        String normalized = tenantId.trim().toLowerCase();
        return TENANT_ID_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private static void setConfig(Connection connection, String name, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config(?, ?, false)")) {
            ps.setString(1, name);
            ps.setString(2, value == null ? "" : value);
            ps.execute();
        }
    }
}
