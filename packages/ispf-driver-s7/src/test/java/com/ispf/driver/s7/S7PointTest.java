package com.ispf.driver.s7;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S7PointTest {

    @Test
    void parsesDbRealMapping() throws Exception {
        S7Point point = S7Point.parse("DB:1:0:REAL");
        assertEquals(1, point.dbNumber());
        assertEquals(0, point.offset());
        assertEquals(S7Point.S7DataType.REAL, point.dataType());
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(Exception.class, () -> S7Point.parse("bad"));
    }
}
