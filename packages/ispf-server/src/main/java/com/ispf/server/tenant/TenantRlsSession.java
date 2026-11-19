package com.ispf.server.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
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

    /** Custom {@code app.*} names a script may set. Rejects anything that is not a plain GUC. */
    private static final Pattern CUSTOM_APP_GUC = Pattern.compile("^app\\.[a-z_][a-z0-9_]*$");

    private static final Pattern SET_CONFIG_LITERAL = Pattern.compile(
            "set_config\\s*\\(\\s*'((?:app)\\.[a-z_][a-z0-9_]*)'",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SET_COMMAND = Pattern.compile(
            "\\bSET\\s+(?:LOCAL\\s+|SESSION\\s+)?((?:app)\\.[a-z_][a-z0-9_]*)",
            Pattern.CASE_INSENSITIVE
    );

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

    /**
     * Reset pooled connection to default-allow before return to pool.
     * A transaction-local {@code set_config} on a custom placeholder GUC leaves {@code ''} after
     * commit, and that empty string is not {@code NULL}. {@code COALESCE(current_setting(...), 'on')}
     * therefore does not restore the allow default. Placeholder GUCs are not listed in
     * {@code pg_settings}, so the names come from the SQL observed on this checkout.
     * Bypass GUCs are set back to {@code on}. Other custom {@code app.*} values are cleared so the
     * next checkout does not keep the previous id.
     */
    public static void clear(Connection connection) throws SQLException {
        clear(connection, Set.of());
    }

    public static void clear(Connection connection, Collection<String> scriptGucs) throws SQLException {
        if (scriptGucs != null) {
            for (String name : scriptGucs) {
                releaseCustomGuc(connection, name);
            }
        }
        setConfig(connection, GUC_BYPASS, "on");
        setConfig(connection, GUC_TENANT_ID, "");
    }

    static void collectCustomGucs(String sql, Set<String> names) {
        if (sql == null || sql.isBlank() || names == null) {
            return;
        }
        collectPattern(SET_CONFIG_LITERAL, sql, names);
        collectPattern(SET_COMMAND, sql, names);
    }

    private static void collectPattern(Pattern pattern, String sql, Set<String> names) {
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            if (CUSTOM_APP_GUC.matcher(name).matches()
                    && !GUC_BYPASS.equals(name)
                    && !GUC_TENANT_ID.equals(name)) {
                names.add(name);
            }
        }
    }

    private static void releaseCustomGuc(Connection connection, String name) throws SQLException {
        if (name == null || !CUSTOM_APP_GUC.matcher(name).matches()
                || GUC_BYPASS.equals(name) || GUC_TENANT_ID.equals(name)) {
            return;
        }
        // Placeholder custom GUCs cannot return to NULL once set in the session.
        // Bypass names use the allow value. Any other name drops the previous value.
        setConfig(connection, name, name.endsWith("_bypass") ? "on" : "");
    }

    static String sanitizeTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "";
        }
        String normalized = tenantId.trim().toLowerCase(Locale.ROOT);
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
