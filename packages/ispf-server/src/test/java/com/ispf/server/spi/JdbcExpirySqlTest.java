package com.ispf.server.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcExpirySqlTest {

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
}
