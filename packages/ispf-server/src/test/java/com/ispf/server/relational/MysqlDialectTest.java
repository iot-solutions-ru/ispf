package com.ispf.server.relational;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlDialectTest {

    private final MysqlDialect dialect = new MysqlDialect();

    @Test
    void refusesSessionSchemaSwitch() {
        assertThat(dialect.supportsSessionSchemaSwitch()).isFalse();
        assertThatThrownBy(() -> dialect.activateSchemaSql("`app_schema`"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ADR-0079");
    }

    @Test
    void resetIsNoOpSelect() {
        assertThat(dialect.resetPlatformSchemaSql()).isEqualTo("SELECT 1");
    }

    @Test
    void uniqueViolationUsesMysqlErrorCodeOnly() {
        assertThat(dialect.isUniqueViolation(sqlEx(1062, "23000"))).isTrue();
        assertThat(dialect.isUniqueViolation(sqlEx(1452, "23000"))).isFalse();
        assertThat(dialect.isUniqueViolation(sqlEx(1048, "23000"))).isFalse();
        assertThat(dialect.isUniqueViolation(sqlEx(0, "23505"))).isFalse();
    }

    @Test
    void booleanAndExpiryAreMysqlShaped() {
        assertThat(dialect.booleanLiteral(true)).isEqualTo("1");
        assertThat(dialect.booleanLiteral(false)).isEqualTo("0");
        assertThat(dialect.nowPlusSecondsExpression()).contains("DATE_ADD");
    }

    @Test
    void nullsLastAndConcatAvoidPostgresOnlySyntax() {
        // MySQL rejects NULLS LAST and reads || as logical OR under the default sql_mode.
        assertThat(dialect.orderByNullsLast("started_at")).isEqualTo("started_at IS NULL, started_at");
        assertThat(dialect.concatExpression("','", "trigger_function_name", "','"))
                .isEqualTo("CONCAT(',', trigger_function_name, ',')");
    }

    @Test
    void bucketSqlStaysPostgresShapedUntilB2() {
        assertThat(dialect.variableSampleBucketAggregateSql()).contains("extract(epoch");
    }

    @Test
    void doesNotClaimTenantRlsOrTimescale() {
        assertThat(dialect.supportsTenantDbRowIsolation()).isFalse();
        assertThat(dialect.supportsTimescaleHypertables()).isFalse();
        assertThat(dialect.flywayLocations()).containsExactly("classpath:db/migration/mysql");
    }

    @Test
    void transactionLockUpsertsLockRowThenLocksIt() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));

        dialect.acquireTransactionLock(connection, "audit_event_chain", 1L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2)).prepareStatement(sql.capture());
        // Exclusive row lock on insert and on duplicate — no shared-lock upgrade deadlock (1213).
        assertThat(sql.getAllValues().get(0)).isEqualTo(
                "INSERT INTO platform_script_locks (lock_key) VALUES (?) "
                        + "ON DUPLICATE KEY UPDATE lock_key = lock_key");
        assertThat(sql.getAllValues().get(1)).endsWith("WHERE lock_key = ? FOR UPDATE");
    }

    private static SQLException sqlEx(int vendorCode, String sqlState) {
        return new SQLException("test", sqlState, vendorCode);
    }
}
