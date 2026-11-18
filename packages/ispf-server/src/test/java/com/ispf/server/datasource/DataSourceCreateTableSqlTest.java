package com.ispf.server.datasource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataSourceCreateTableSqlTest {

    @Test
    void quotesIdentifiersAndPrimaryKey() {
        String sql = DataSourceMetadataService.buildCreateTable("app_demo", "items", List.of(
                new DataSourceMetadataService.ColumnSpec("id", "INTEGER", null, false, true),
                new DataSourceMetadataService.ColumnSpec("name", "VARCHAR", 64, true, false)
        ));
        assertEquals(
                "CREATE TABLE \"app_demo\".\"items\" (\"id\" INTEGER NOT NULL, \"name\" VARCHAR(64), PRIMARY KEY (\"id\"))",
                sql
        );
    }

    @Test
    void rejectsUnsafeTypeAndIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> DataSourceMetadataService.buildCreateTable(
                "app_demo",
                "items",
                List.of(new DataSourceMetadataService.ColumnSpec("id", "INTEGER; DROP TABLE x", null, false, true))
        ));
        assertThrows(IllegalArgumentException.class, () -> DataSourceMetadataService.buildCreateTable(
                "app_demo",
                "bad-name",
                List.of(new DataSourceMetadataService.ColumnSpec("id", "INTEGER", null, false, true))
        ));
    }
}
