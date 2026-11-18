package com.ispf.server.application.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FunctionScriptTimestampZoneTest {

    @Test
    void platformScriptSqlUsesUtcForTimestampWithoutZone() {
        assertEquals("SET LOCAL TIME ZONE 'UTC'", FunctionScriptEngine.timestampZoneSql("PostgreSQL"));
        assertEquals("SET TIME ZONE 'UTC'", FunctionScriptEngine.timestampZoneSql("H2"));
        assertNull(FunctionScriptEngine.timestampZoneSql("MySQL"));
    }
}
