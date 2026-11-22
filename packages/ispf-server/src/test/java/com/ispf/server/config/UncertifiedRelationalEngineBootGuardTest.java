package com.ispf.server.config;

import com.ispf.server.relational.H2Dialect;
import com.ispf.server.relational.MssqlDialect;
import com.ispf.server.relational.MysqlDialect;
import com.ispf.server.relational.OracleDialect;
import com.ispf.server.relational.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UncertifiedRelationalEngineBootGuardTest {

    @Test
    void refusesOracle() {
        assertThatThrownBy(() -> new UncertifiedRelationalEngineBootGuard(new OracleDialect())
                .refuseUncertifiedEngines())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oracle")
                .hasMessageContaining("ADR-0080");
    }

    @Test
    void allowsPostgresH2Mssql() {
        assertThatCode(() -> new UncertifiedRelationalEngineBootGuard(new PostgreSqlDialect())
                .refuseUncertifiedEngines()).doesNotThrowAnyException();
        assertThatCode(() -> new UncertifiedRelationalEngineBootGuard(new H2Dialect())
                .refuseUncertifiedEngines()).doesNotThrowAnyException();
        assertThatCode(() -> new UncertifiedRelationalEngineBootGuard(new MssqlDialect())
                .refuseUncertifiedEngines()).doesNotThrowAnyException();
    }

    @Test
    void allowsMysqlBecauseB1FencesGuardIt() {
        // ADR-0079 Lab green: the engine gate passes; MysqlCertificationBootGuard holds the fences.
        assertThatCode(() -> new UncertifiedRelationalEngineBootGuard(new MysqlDialect())
                .refuseUncertifiedEngines()).doesNotThrowAnyException();
    }
}
