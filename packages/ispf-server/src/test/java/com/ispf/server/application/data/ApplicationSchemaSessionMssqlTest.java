package com.ispf.server.application.data;

import com.ispf.server.relational.MssqlDialect;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationSchemaSessionMssqlTest {

    @Test
    void refusesSessionSchemaSwitchOnMssql() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl("jdbc:h2:mem:schema-mssql-guard;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ApplicationSchemaSession session = new ApplicationSchemaSession(ds, new MssqlDialect());
        assertThatThrownBy(() -> session.runInSchema("app_demo", () -> {
        }))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ADR-0078");
    }
}
