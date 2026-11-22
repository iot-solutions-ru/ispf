package com.ispf.server.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcExpirySqlTest {

    @Test
    void oracleStyleForOracleProductName() {
        assertThat(JdbcExpirySql.nowPlusSecondsExpression("Oracle"))
                .isEqualTo(JdbcExpirySql.ORACLE_STYLE);
        assertThat(JdbcExpirySql.ORACLE_STYLE).doesNotContain("BIGINT");
    }

    @Test
    void detectsSqlServerProductNames() {
        assertThat(JdbcExpirySql.nowPlusSecondsExpression("Microsoft SQL Server"))
                .isEqualTo(JdbcExpirySql.MSSQL_STYLE);
        assertThat(JdbcExpirySql.nowPlusSecondsExpression("SQL Server"))
                .isEqualTo(JdbcExpirySql.MSSQL_STYLE);
    }

    @Test
    void defaultsToPostgresStyle() {
        assertThat(JdbcExpirySql.nowPlusSecondsExpression("PostgreSQL"))
                .isEqualTo(JdbcExpirySql.POSTGRES_STYLE);
        assertThat(JdbcExpirySql.nowPlusSecondsExpression("H2"))
                .isEqualTo(JdbcExpirySql.POSTGRES_STYLE);
        assertThat(JdbcExpirySql.nowPlusSecondsExpression(null))
                .isEqualTo(JdbcExpirySql.POSTGRES_STYLE);
    }

    @Test
    void mysqlStyleOnlyForMysqlNotMariadb() {
        assertThat(JdbcExpirySql.nowPlusSecondsExpression("MySQL"))
                .isEqualTo(JdbcExpirySql.MYSQL_STYLE);
        assertThat(JdbcExpirySql.nowPlusSecondsExpression("MariaDB"))
                .isEqualTo(JdbcExpirySql.POSTGRES_STYLE);
        assertThat(JdbcExpirySql.nowPlusSecondsExpression("MySQL / MariaDB"))
                .isEqualTo(JdbcExpirySql.POSTGRES_STYLE);
    }
}
