package com.ispf.server.relational;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MssqlDialectTest {

    private final MssqlDialect dialect = new MssqlDialect();

    @Test
    void refusesSessionSchemaSwitch() {
        assertThat(dialect.supportsSessionSchemaSwitch()).isFalse();
        assertThatThrownBy(() -> dialect.activateSchemaSql("[app_schema]"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ADR-0078");
    }

    @Test
    void resetIsNoOpSelect() {
        assertThat(dialect.resetPlatformSchemaSql()).isEqualTo("SELECT 1");
    }

    @Test
    void limitAndTopOneUseTSql() {
        assertThat(dialect.limitClause()).contains("FETCH NEXT");
        assertThat(dialect.selectTopOneForUpdate("chain_hash", "audit_event_chain", "seq DESC"))
                .contains("TOP (1)")
                .contains("UPDLOCK")
                .doesNotContain("LIMIT")
                .doesNotContain("FOR UPDATE");
    }

    @Test
    void nullsLastAndConcatUseTSql() {
        assertThat(dialect.orderByNullsLast("started_at"))
                .isEqualTo("CASE WHEN started_at IS NULL THEN 1 ELSE 0 END, started_at");
        assertThat(dialect.concatExpression("','", "trigger_function_name", "','"))
                .isEqualTo("(',' + trigger_function_name + ',')");
    }

    @Test
    void uniqueViolationUsesSqlServerErrorCodesOnly() {
        assertThat(dialect.isUniqueViolation(sqlEx(2627, "23000"))).isTrue();
        assertThat(dialect.isUniqueViolation(sqlEx(2601, "23000"))).isTrue();
        assertThat(dialect.isUniqueViolation(sqlEx(547, "23000"))).isFalse();
        assertThat(dialect.isUniqueViolation(sqlEx(0, "23505"))).isFalse();
    }

    @Test
    void doesNotClaimTenantRlsOrTimescale() {
        assertThat(dialect.supportsTenantDbRowIsolation()).isFalse();
        assertThat(dialect.supportsTimescaleHypertables()).isFalse();
        assertThat(dialect.flywayLocations()).containsExactly("classpath:db/migration/mssql");
    }

    private static SQLException sqlEx(int vendorCode, String sqlState) {
        return new SQLException("test", sqlState, vendorCode);
    }
}
