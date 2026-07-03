package com.ispf.server.application.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationSchemaSupportTest {

    @Test
    void acceptsSimpleSelectQueries() {
        assertDoesNotThrow(() -> ApplicationSchemaSupport.validateSelectQuery("SELECT 1", "Query"));
        assertDoesNotThrow(() -> ApplicationSchemaSupport.validateSelectQuery(
                "WITH cte AS (SELECT 1) SELECT * FROM cte",
                "Query"
        ));
    }

    @Test
    void rejectsMutationStatements() {
        assertThrows(IllegalArgumentException.class, () ->
                ApplicationSchemaSupport.validateSelectQuery("DELETE FROM demo_item", "Query"));
        assertThrows(IllegalArgumentException.class, () ->
                ApplicationSchemaSupport.validateSelectQuery("SELECT 1; DROP TABLE demo_item", "Query"));
    }
}
