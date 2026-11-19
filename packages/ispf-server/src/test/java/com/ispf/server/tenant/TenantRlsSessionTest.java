package com.ispf.server.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantRlsSessionTest {

    @AfterEach
    void clearContext() {
        TenantRlsContext.clear();
    }

    @Test
    void applySetsBypassAndTenantIdViaSetConfig() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement("SELECT set_config(?, ?, false)")).thenReturn(ps);

        TenantRlsSession.apply(connection, TenantRlsContext.State.forTenant("acme"));

        verify(connection, times(2)).prepareStatement("SELECT set_config(?, ?, false)");
        verify(ps).setString(1, TenantRlsSession.GUC_BYPASS);
        verify(ps).setString(2, "off");
        verify(ps).setString(1, TenantRlsSession.GUC_TENANT_ID);
        verify(ps).setString(2, "acme");
        verify(ps, times(2)).execute();
        verify(ps, times(2)).close();
    }

    @Test
    void applyNullStateDefaultsToBypassOn() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);

        TenantRlsSession.apply(connection, null);

        verify(ps).setString(1, TenantRlsSession.GUC_BYPASS);
        verify(ps).setString(2, "on");
        verify(ps).setString(1, TenantRlsSession.GUC_TENANT_ID);
        verify(ps).setString(2, "");
    }

    @Test
    void clearResetsToBypass() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);

        TenantRlsSession.clear(connection);

        verify(ps).setString(2, "on");
        verify(ps).setString(2, "");
    }

    @Test
    void clearRestoresAllowForScriptGucs() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);

        TenantRlsSession.clear(connection, List.of("app.site_bypass", "app.site_id", "app.bad-name"));

        verify(ps).setString(1, "app.site_bypass");
        verify(ps).setString(1, "app.site_id");
        verify(ps, times(2)).setString(2, "on");
        verify(ps, times(2)).setString(2, "");
    }

    @Test
    void collectReadsCustomGucsFromScriptSql() {
        Set<String> names = new LinkedHashSet<>();
        TenantRlsSession.collectCustomGucs(
                "SELECT set_config('app.site_bypass', 'off', true); SELECT set_config('app.site_id', 'site-a', true)",
                names
        );
        TenantRlsSession.collectCustomGucs("SET LOCAL app.site_region = 'eu'", names);
        TenantRlsSession.collectCustomGucs("SELECT set_config('app.tenant_bypass', 'off', true)", names);

        assertThat(names).containsExactly("app.site_bypass", "app.site_id", "app.site_region");
    }

    @Test
    void sanitizeRejectsUnsafeTenantIds() {
        assertThat(TenantRlsSession.sanitizeTenantId("acme")).isEqualTo("acme");
        assertThat(TenantRlsSession.sanitizeTenantId("plant-1")).isEqualTo("plant-1");
        assertThat(TenantRlsSession.sanitizeTenantId("acme'; DROP TABLE")).isEmpty();
        assertThat(TenantRlsSession.sanitizeTenantId("ACME")).isEqualTo("acme");
        assertThat(TenantRlsSession.sanitizeTenantId(null)).isEmpty();
    }

    @Test
    void unsafeTenantIdFallsBackToBypass() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);

        TenantRlsSession.apply(connection, TenantRlsContext.State.forTenant("bad;id"));

        verify(ps).setString(2, "on");
        verify(ps).setString(2, "");
    }
}
