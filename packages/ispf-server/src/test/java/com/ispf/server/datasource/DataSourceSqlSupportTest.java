package com.ispf.server.datasource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataSourceSqlSupportTest {

    @Test
    void allowsWriteOnInternalDataSource() {
        assertDoesNotThrow(() ->
                DataSourceSqlSupport.assertAllowedForExternal(false, "DELETE FROM ds_exec_test"));
    }

    @Test
    void blocksWriteOnExternalDataSource() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DataSourceSqlSupport.assertAllowedForExternal(true, "DELETE FROM users")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DataSourceSqlSupport.assertAllowedForExternal(true, "DROP TABLE users")
        );
    }

    @Test
    void allowsSelectOnExternalDataSource() {
        assertDoesNotThrow(() ->
                DataSourceSqlSupport.assertAllowedForExternal(true, "SELECT 1"));
    }
}
