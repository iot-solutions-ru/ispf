package com.ispf.server.relational;

import com.ispf.server.spi.JdbcExpirySql;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OracleDialectTest {

    private final OracleDialect dialect = new OracleDialect();

    @Test
    void refusesSessionSchemaSwitch() {
        assertThat(dialect.supportsSessionSchemaSwitch()).isFalse();
        assertThatThrownBy(() -> dialect.activateSchemaSql("\"APP_SCHEMA\""))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ADR-0080");
        assertThatThrownBy(() -> dialect.createSchemaIfNotExistsSql("\"APP_SCHEMA\""))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void resetIsNoOpSelectFromDual() {
        assertThat(dialect.resetPlatformSchemaSql()).isEqualTo("SELECT 1 FROM DUAL");
    }

    @Test
    void uniqueViolationUsesOra00001Only() {
        assertThat(dialect.isUniqueViolation(sqlEx(1, "23000"))).isTrue();
        assertThat(dialect.isUniqueViolation(sqlEx(2291, "23000"))).isFalse();
        assertThat(dialect.isUniqueViolation(sqlEx(2292, "23000"))).isFalse();
        assertThat(dialect.isUniqueViolation(sqlEx(1400, "23000"))).isFalse();
        assertThat(dialect.isUniqueViolation(sqlEx(2290, "23000"))).isFalse();
        assertThat(dialect.isUniqueViolation(sqlEx(0, "23505"))).isFalse();
        assertThat(dialect.isUniqueViolation(null)).isFalse();
    }

    @Test
    void uniqueViolationIgnoresMessageHeuristics() {
        assertThat(dialect.isUniqueViolation(new SQLIntegrityConstraintViolationException(
                "ORA-01400: cannot insert NULL into (\"ISPF\".\"T\".\"PRIMARY_KEY_ID\")", "23000", 1400
        ))).isFalse();
        assertThat(dialect.isUniqueViolation(new SQLIntegrityConstraintViolationException(
                "duplicate key", "23000", 0
        ))).isFalse();
    }

    @Test
    void quoteIdentifierFoldsToDictionaryCase() {
        assertThat(dialect.quoteIdentifier("platform_jobs")).isEqualTo("\"PLATFORM_JOBS\"");
    }

    @Test
    void quoteIdentifierFailsClosedOnCharactersOracleCannotQuote() {
        assertThatThrownBy(() -> dialect.quoteIdentifier("bad\"name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADR-0080");
        assertThatThrownBy(() -> dialect.quoteIdentifier("bad\0name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADR-0080");
        assertThatThrownBy(() -> dialect.quoteIdentifier(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dialect.quoteIdentifier(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void booleanAndExpiryAreOracleShaped() {
        assertThat(dialect.booleanLiteral(true)).isEqualTo("1");
        assertThat(dialect.booleanLiteral(false)).isEqualTo("0");
        assertThat(dialect.nowPlusSecondsExpression())
                .isEqualTo(JdbcExpirySql.ORACLE_STYLE)
                .contains("NUMTODSINTERVAL")
                .doesNotContain("BIGINT");
    }

    @Test
    void limitClauseUsesRowLimitingWithOneBind() {
        assertThat(dialect.limitClause()).isEqualTo(" FETCH FIRST ? ROWS ONLY");
    }

    @Test
    void topOneForUpdateLocksTheHeadRowWithoutRowLimit() {
        // 19c: ORA-02014 for FETCH FIRST + FOR UPDATE; a bare FOR UPDATE would lock every row.
        assertThat(dialect.selectTopOneForUpdate("chain_hash", "audit_event_chain", "seq DESC")).isEqualTo(
                "SELECT chain_hash FROM audit_event_chain WHERE ROWID = (SELECT head_rowid FROM "
                        + "(SELECT ROWID AS head_rowid FROM audit_event_chain ORDER BY seq DESC) "
                        + "WHERE ROWNUM = 1) FOR UPDATE");
    }

    @Test
    void jobClaimSkipsLockedRowsWithoutRowLimit() {
        String sql = dialect.queuedPlatformJobSelectSql();
        assertThat(sql).contains("ORDER BY priority DESC, created_at").endsWith("FOR UPDATE SKIP LOCKED\n");
        assertThat(sql).doesNotContain("FETCH").doesNotContain("ROWNUM").doesNotContain("LIMIT");
    }

    @Test
    void transactionLockInsertsFromDualThenLocksTheRow() throws SQLException {
        Connection connection = connectionReturning(mock(PreparedStatement.class));

        dialect.acquireTransactionLock(connection, "audit_event_chain", 1L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2)).prepareStatement(sql.capture());
        assertThat(sql.getAllValues().get(0)).isEqualToIgnoringWhitespace(
                "INSERT INTO platform_script_locks (lock_key) SELECT ? FROM DUAL "
                        + "WHERE NOT EXISTS (SELECT 1 FROM platform_script_locks WHERE lock_key = ?)");
        assertThat(sql.getAllValues().get(1)).endsWith("WHERE lock_key = ? FOR UPDATE");
    }

    @Test
    void racingLockRowInsertIgnoresOnlyOra00001() throws SQLException {
        PreparedStatement duplicate = mock(PreparedStatement.class);
        when(duplicate.executeUpdate()).thenThrow(sqlEx(1, "23000"));
        when(duplicate.executeQuery()).thenReturn(mock(ResultSet.class));
        dialect.acquireTransactionLock(connectionReturning(duplicate), "k", 1L);

        PreparedStatement notNull = mock(PreparedStatement.class);
        when(notNull.executeUpdate()).thenThrow(sqlEx(1400, "23000"));
        assertThatThrownBy(() -> dialect.acquireTransactionLock(connectionReturning(notNull), "k", 1L))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void nullsLastAndConcatUseOracleNativeSyntax() {
        assertThat(dialect.orderByNullsLast("started_at")).isEqualTo("started_at NULLS LAST");
        assertThat(dialect.concatExpression("','", "trigger_function_name", "','"))
                .isEqualTo("(',' || trigger_function_name || ',')");
    }

    @Test
    void bucketSqlIsNativeOracleForB2() {
        String sql = dialect.variableSampleBucketAggregateSql();
        assertThat(sql).contains("NUMTODSINTERVAL");
        assertThat(sql).contains("FETCH FIRST ? ROWS ONLY");
        assertThat(sql).doesNotContain("extract(epoch");
    }

    @Test
    void doesNotClaimTenantRlsOrTimescale() {
        assertThat(dialect.supportsTenantDbRowIsolation()).isFalse();
        assertThat(dialect.supportsTimescaleHypertables()).isFalse();
        assertThat(dialect.flywayLocations()).containsExactly("classpath:db/migration/oracle");
    }

    private static Connection connectionReturning(PreparedStatement statement) throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("Oracle");
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        return connection;
    }

    private static SQLException sqlEx(int vendorCode, String sqlState) {
        return new SQLException("test", sqlState, vendorCode);
    }
}
