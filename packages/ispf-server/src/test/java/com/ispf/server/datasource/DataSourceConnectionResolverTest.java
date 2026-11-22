package com.ispf.server.datasource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceConnectionResolverTest {

    @Test
    void infersPostgresDriverFromUrl() throws Exception {
        var method = DataSourceConnectionResolver.class.getDeclaredMethod("inferDriverClass", String.class);
        method.setAccessible(true);
        assertThat(method.invoke(null, "jdbc:postgresql://localhost/ispf"))
                .isEqualTo("org.postgresql.Driver");
    }

    @Test
    void mysqlUrlFallsBackToShippedMariadbDriverWithoutConnectorJ() throws Exception {
        var method = DataSourceConnectionResolver.class.getDeclaredMethod("inferDriverClass", String.class);
        method.setAccessible(true);
        // GPL mysql-connector-j is not on the classpath (ADR-0079).
        assertThat(method.invoke(null, "jdbc:mysql://db:3306/reports"))
                .isEqualTo("org.mariadb.jdbc.Driver");
        assertThat(method.invoke(null, "jdbc:mariadb://db:3306/reports"))
                .isEqualTo("org.mariadb.jdbc.Driver");
    }

    @Test
    void mariadbDriverOnMysqlUrlGetsPermitMysqlScheme() {
        assertThat(DataSourceConnectionResolver.permitMysqlSchemeForMariaDb(
                "jdbc:mysql://db:3306/reports", "org.mariadb.jdbc.Driver"))
                .isEqualTo("jdbc:mysql://db:3306/reports?permitMysqlScheme=true");
        assertThat(DataSourceConnectionResolver.permitMysqlSchemeForMariaDb(
                "jdbc:mysql://db:3306/reports?sslMode=verify-full", "org.mariadb.jdbc.Driver"))
                .isEqualTo("jdbc:mysql://db:3306/reports?sslMode=verify-full&permitMysqlScheme=true");
        assertThat(DataSourceConnectionResolver.permitMysqlSchemeForMariaDb(
                "jdbc:mysql://db:3306/reports?permitMysqlScheme=true", "org.mariadb.jdbc.Driver"))
                .isEqualTo("jdbc:mysql://db:3306/reports?permitMysqlScheme=true");
    }

    @Test
    void otherDriversAndSchemesKeepTheirUrl() {
        assertThat(DataSourceConnectionResolver.permitMysqlSchemeForMariaDb(
                "jdbc:mariadb://db:3306/reports", "org.mariadb.jdbc.Driver"))
                .isEqualTo("jdbc:mariadb://db:3306/reports");
        assertThat(DataSourceConnectionResolver.permitMysqlSchemeForMariaDb(
                "jdbc:mysql://db:3306/reports", "com.mysql.cj.jdbc.Driver"))
                .isEqualTo("jdbc:mysql://db:3306/reports");
    }
}
