package com.ispf.server.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Optional PostgreSQL integration check for RLS A≠B.
 * <p>
 * Set {@code ISPF_RLS_IT_JDBC_URL} (and optional user/password) to run against a real DB
 * that has already applied {@code V86__tenant_row_level_security.sql}.
 * Skipped on H2 / CI by default.
 */
@EnabledIfEnvironmentVariable(named = "ISPF_RLS_IT_JDBC_URL", matches = ".+")
class TenantRlsJdbcIsolationIT {

    @AfterEach
    void clear() {
        TenantRlsContext.clear();
    }

    @Test
    void crossTenantSelectIsEmptyWhenBypassOff() throws Exception {
        String url = System.getenv("ISPF_RLS_IT_JDBC_URL");
        assumeTrue(url != null && url.toLowerCase().contains("postgresql"));
        String user = envOr("ISPF_RLS_IT_DB_USER", "ispf");
        String password = envOr("ISPF_RLS_IT_DB_PASSWORD", "ispf");

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assumeTrue(tableExists(connection, "object_nodes"));

            String acmePath = "root.tenant.rlsit-acme.platform";
            String betaPath = "root.tenant.rlsit-beta.platform";
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DELETE FROM object_nodes WHERE path IN ('" + acmePath + "', '" + betaPath + "')");
                st.executeUpdate("""
                        INSERT INTO object_nodes (id, path, type, display_name)
                        VALUES
                          ('rlsit-acme-platform', '%s', 'FOLDER', 'Acme'),
                          ('rlsit-beta-platform', '%s', 'FOLDER', 'Beta')
                        """.formatted(acmePath, betaPath));
            }

            TenantRlsSession.apply(connection, TenantRlsContext.State.forTenant("rlsit-acme"));
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT path FROM object_nodes WHERE path IN (?, ?)"
            )) {
                ps.setString(1, acmePath);
                ps.setString(2, betaPath);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo(acmePath);
                    assertThat(rs.next()).isFalse();
                }
            }

            TenantRlsSession.clear(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM object_nodes WHERE path IN (?, ?)"
            )) {
                ps.setString(1, acmePath);
                ps.setString(2, betaPath);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
            }

            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DELETE FROM object_nodes WHERE path IN ('" + acmePath + "', '" + betaPath + "')");
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        }
    }

    private static String envOr(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
