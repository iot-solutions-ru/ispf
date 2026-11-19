package com.ispf.server.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantRlsHikariDataSourceTest {

    @AfterEach
    void clearContext() {
        TenantRlsContext.clear();
    }

    @Test
    void invokeUnwrappedRethrowsSqlExceptionDirectly() throws Exception {
        Statement statement = mock(Statement.class);
        SQLException boom = new SQLException("boom", "42000");
        when(statement.execute("SELECT 1")).thenThrow(boom);
        Method execute = Statement.class.getMethod("execute", String.class);

        assertThatThrownBy(() -> TenantRlsHikariDataSource.invokeUnwrapped(statement, execute, new Object[]{"SELECT 1"}))
                .isSameAs(boom)
                .isNotInstanceOf(UndeclaredThrowableException.class);
    }

    @Test
    void statementSqlExceptionPropagatesAsSqlException() throws Exception {
        TenantRlsHikariDataSource pool = spy(new TenantRlsHikariDataSource());
        Connection raw = mock(Connection.class);
        PreparedStatement applyPs = mock(PreparedStatement.class);
        when(raw.prepareStatement(anyString())).thenReturn(applyPs);
        when(raw.getAutoCommit()).thenReturn(true);
        Statement statement = mock(Statement.class);
        when(raw.createStatement()).thenReturn(statement);
        SQLException boom = new SQLException("boom", "42000");
        when(statement.execute("SELECT 1")).thenThrow(boom);

        Connection wrapped = pool.wrapForTests(raw);
        Statement wrappedStatement = wrapped.createStatement();

        assertThatThrownBy(() -> wrappedStatement.execute("SELECT 1"))
                .isSameAs(boom)
                .isNotInstanceOf(UndeclaredThrowableException.class);
    }

    @Test
    void closeRollsBackBeforeClearWhenAutoCommitIsOff() throws Exception {
        TenantRlsHikariDataSource pool = spy(new TenantRlsHikariDataSource());
        Connection raw = mock(Connection.class);
        PreparedStatement applyPs = mock(PreparedStatement.class);
        when(raw.prepareStatement(anyString())).thenReturn(applyPs);
        when(raw.getAutoCommit()).thenReturn(false);

        Connection wrapped = pool.wrapForTests(raw);
        wrapped.createStatement(); // collect nothing; still exercises create path
        wrapped.close();

        var inOrder = org.mockito.Mockito.inOrder(raw);
        inOrder.verify(raw).rollback();
        inOrder.verify(raw, atLeastOnce()).prepareStatement("SELECT set_config(?, ?, false)");
        inOrder.verify(raw).close();
        verify(pool, never()).evictConnection(raw);
    }

    @Test
    void clearFailureEvictsPooledConnection() throws Exception {
        TenantRlsHikariDataSource pool = spy(new TenantRlsHikariDataSource());
        Connection raw = mock(Connection.class);
        PreparedStatement applyPs = mock(PreparedStatement.class);
        when(raw.prepareStatement(anyString())).thenReturn(applyPs);
        when(raw.getAutoCommit()).thenReturn(true);

        Connection wrapped = pool.wrapForTests(raw);
        when(raw.prepareStatement(anyString())).thenThrow(new SQLException("aborted", "25P02"));

        wrapped.close();

        verify(pool).evictConnection(raw);
        verify(raw).close();
    }

    @Test
    void executeCollectsCustomGucsThenClearRestoresThem() throws Exception {
        TenantRlsHikariDataSource pool = spy(new TenantRlsHikariDataSource());
        Connection raw = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(raw.prepareStatement(anyString())).thenReturn(ps);
        when(raw.getAutoCommit()).thenReturn(true);
        Statement statement = mock(Statement.class);
        when(raw.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);

        Connection wrapped = pool.wrapForTests(raw);
        wrapped.createStatement().execute("SELECT set_config('app.site_bypass', 'off', true)");
        wrapped.close();

        verify(ps, atLeastOnce()).setString(1, "app.site_bypass");
        verify(ps, atLeastOnce()).setString(2, "on");
        assertThat(true).isTrue();
    }
}
