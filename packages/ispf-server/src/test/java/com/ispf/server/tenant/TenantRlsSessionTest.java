package com.ispf.server.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

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
