package com.ispf.server.relational;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void bucketSqlStaysPostgresShapedUntilB2() {
        assertThat(dialect.variableSampleBucketAggregateSql()).contains("extract(epoch");
    }

    @Test
    void doesNotClaimTenantRlsOrTimescale() {
        assertThat(dialect.supportsTenantDbRowIsolation()).isFalse();
        assertThat(dialect.supportsTimescaleHypertables()).isFalse();
        assertThat(dialect.flywayLocations()).containsExactly("classpath:db/migration/mysql");
    }

    private static SQLException sqlEx(int vendorCode, String sqlState) {
        return new SQLException("test", sqlState, vendorCode);
    }
}
