package com.ispf.server.relational;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSqlDialectTest {

    @Test
    void createsSchemaBeforeSearchPathSwitch() {
        // SET search_path TO a missing schema does not fail, but CREATE TABLE then hits 3F000.
        assertTrue(new PostgreSqlDialect().requiresSchemaDdlBeforeSwitch());
    }
}
