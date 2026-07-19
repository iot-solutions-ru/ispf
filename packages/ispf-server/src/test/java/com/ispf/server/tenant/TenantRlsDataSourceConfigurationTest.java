package com.ispf.server.tenant;

import com.ispf.server.config.TenantIsolationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRlsDataSourceConfigurationTest {

    @Test
    void appliesOnlyForPostgresWhenFlagEnabled() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(true);
        DataSourceProperties ds = new DataSourceProperties();
        ds.setUrl("jdbc:postgresql://localhost:5432/ispf");
        assertThat(TenantRlsDataSourceConfiguration.shouldApplyRls(ds, props)).isTrue();

        // H2 PG-compat URLs must not enable RLS wrapping.
        ds.setUrl("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        assertThat(TenantRlsDataSourceConfiguration.shouldApplyRls(ds, props)).isFalse();
    }

    @Test
    void disabledWhenFlagOff() {
        TenantIsolationProperties props = new TenantIsolationProperties();
        props.setDbRowIsolation(false);
        DataSourceProperties ds = new DataSourceProperties();
        ds.setUrl("jdbc:postgresql://localhost:5432/ispf");
        assertThat(TenantRlsDataSourceConfiguration.shouldApplyRls(ds, props)).isFalse();
    }
}
