package com.ispf.server.relational;

import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OracleHibernateInstantsTest {

    private final RelationalConfiguration configuration = new RelationalConfiguration();

    @Test
    void bindsInstantAsPlainTimestampOnOracle() {
        Map<String, Object> properties = new HashMap<>();
        configuration.oracleHibernateInstants(new OracleDialect()).customize(properties);
        assertThat(properties).containsEntry(AvailableSettings.PREFERRED_INSTANT_JDBC_TYPE, "TIMESTAMP");
    }

    @Test
    void keepsAnExplicitSetting() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(AvailableSettings.PREFERRED_INSTANT_JDBC_TYPE, "INSTANT");
        configuration.oracleHibernateInstants(new OracleDialect()).customize(properties);
        assertThat(properties).containsEntry(AvailableSettings.PREFERRED_INSTANT_JDBC_TYPE, "INSTANT");
    }

    @Test
    void leavesOtherEnginesOnHibernateDefaults() {
        Map<String, Object> properties = new HashMap<>();
        for (RelationalDialect dialect : new RelationalDialect[]{
                new PostgreSqlDialect(), new MssqlDialect(), new MysqlDialect(), new H2Dialect()}) {
            configuration.oracleHibernateInstants(dialect).customize(properties);
        }
        assertThat(properties).doesNotContainKey(AvailableSettings.PREFERRED_INSTANT_JDBC_TYPE);
    }
}
