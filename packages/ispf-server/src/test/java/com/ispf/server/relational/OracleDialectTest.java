package com.ispf.server.relational;

import com.ispf.server.spi.JdbcExpirySql;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void bucketSqlStaysPostgresShapedUntilB2() {
        assertThat(dialect.variableSampleBucketAggregateSql()).contains("extract(epoch");
    }

    @Test
    void doesNotClaimTenantRlsOrTimescale() {
        assertThat(dialect.supportsTenantDbRowIsolation()).isFalse();
        assertThat(dialect.supportsTimescaleHypertables()).isFalse();
        assertThat(dialect.flywayLocations()).containsExactly("classpath:db/migration/oracle");
    }

    private static SQLException sqlEx(int vendorCode, String sqlState) {
        return new SQLException("test", sqlState, vendorCode);
    }
}
