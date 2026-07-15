package com.ispf.server.application.data;

import com.ispf.server.relational.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationSchemaSessionTest {

    @Test
    void runInSchemaNeverCreatesSchemaOnTransactionalConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection poolConn = mock(Connection.class);
        Connection txConn = mock(Connection.class);
        Statement poolStatement = mock(Statement.class);
        Statement txStatement = mock(Statement.class);

        // First call: ensureSchemaExists borrows a fresh pool connection.
        // Subsequent calls from DataSourceUtils in non-Spring tests use the mock as-is —
        // we drive runInSchema by stubbing getConnection for both paths carefully.
        AtomicInteger getConnectionCalls = new AtomicInteger();
        when(dataSource.getConnection()).thenAnswer(inv -> {
            if (getConnectionCalls.getAndIncrement() == 0) {
                return poolConn;
            }
            return txConn;
        });
        when(poolConn.createStatement()).thenReturn(poolStatement);
        when(txConn.createStatement()).thenReturn(txStatement);
        when(txConn.getSchema()).thenReturn("public");
        when(txConn.isReadOnly()).thenReturn(true);
        when(txConn.getAutoCommit()).thenReturn(false);

        ApplicationSchemaSession session = new ApplicationSchemaSession(dataSource, new PostgreSqlDialect());

        // Without Spring transaction sync, DataSourceUtils.getConnection also calls dataSource.getConnection().
        assertThatCode(() -> session.runInSchema("app_employees_app", () -> { }))
                .doesNotThrowAnyException();

        verify(poolStatement, atLeastOnce()).execute(anyString()); // CREATE SCHEMA IF NOT EXISTS on pool conn
        // Transactional/search_path connection must only SET search_path — never CREATE.
        verify(txStatement, atLeastOnce()).execute("SET search_path TO app_employees_app");
        verify(txStatement, atLeastOnce()).execute("SET search_path TO public");
        verify(txStatement, never()).execute(org.mockito.ArgumentMatchers.contains("CREATE SCHEMA"));
    }

    @Test
    void quoteIdentifierRejectsHyphenatedNames() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationSchemaSupport.quoteIdentifier("employees-app")
        ).getMessage()).contains("Invalid SQL identifier");
    }
}
