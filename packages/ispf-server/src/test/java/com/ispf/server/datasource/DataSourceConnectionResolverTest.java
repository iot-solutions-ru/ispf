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
}
