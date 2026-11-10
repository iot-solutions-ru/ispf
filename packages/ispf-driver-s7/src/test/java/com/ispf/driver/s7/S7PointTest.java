package com.ispf.driver.s7;

import com.ispf.driver.DriverConfigurationException;
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
    void parsesBoolBitInsideTheByte() throws Exception {
        S7Point point = S7Point.parse("DB:1:0.3:BOOL");
        assertEquals(0, point.offset());
        assertEquals(3, point.bitOffset());
        assertEquals(S7Point.S7DataType.BOOL, point.dataType());
        assertEquals(0, S7Point.parse("DB:1:0:BOOL").bitOffset());
    }

    @Test
    void rejectsBoolBitOutsideZeroToSeven() throws Exception {
        assertThrows(DriverConfigurationException.class, () -> S7Point.parse("DB:1:0.8:BOOL"));
        assertThrows(DriverConfigurationException.class, () -> S7Point.parse("DB:1:0.3:REAL"));
        assertThrows(DriverConfigurationException.class, () -> S7Point.parse("DB:1:0.0:REAL"));
        assertEquals(7, S7Point.parse("DB:1:0.7:BOOL").bitOffset());
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(Exception.class, () -> S7Point.parse("bad"));
    }
}
