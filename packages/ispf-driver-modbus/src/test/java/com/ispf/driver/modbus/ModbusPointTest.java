package com.ispf.driver.modbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModbusPointTest {

    @Test
    void parsesHoldingRegisterMapping() throws Exception {
        ModbusPoint point = ModbusPoint.parse("1:HOLDING:10");
        assertEquals(1, point.slaveId());
        assertEquals(ModbusPoint.RegisterType.HOLDING, point.type());
        assertEquals(10, point.address());
        assertEquals(1, point.count());
        assertEquals(ModbusPoint.DataType.RAW, point.dataType());
        assertEquals(ModbusPoint.ByteOrder.ABCD, point.byteOrder());
    }

    @Test
    void parsesHoldingRegisterMappingWithCount() throws Exception {
        ModbusPoint point = ModbusPoint.parse("1:HOLDING:10:2");
        assertEquals(2, point.count());
        assertEquals(ModbusPoint.DataType.RAW, point.dataType());
    }

    @Test
    void parsesCanonicalFloat32Badc() throws Exception {
        ModbusPoint point = ModbusPoint.parse("1:HOLDING:0:FLOAT32:BADC");
        assertEquals(0, point.address());
        assertEquals(2, point.count());
        assertEquals(ModbusPoint.DataType.FLOAT32, point.dataType());
        assertEquals(ModbusPoint.ByteOrder.BADC, point.byteOrder());
    }

    @Test
    void parsesFloat32WithExplicitCount() throws Exception {
        ModbusPoint point = ModbusPoint.parse("1:HOLDING:0:2:FLOAT32:BADC");
        assertEquals(2, point.count());
        assertEquals(ModbusPoint.DataType.FLOAT32, point.dataType());
    }

    @Test
    void parsesDouble() throws Exception {
        ModbusPoint point = ModbusPoint.parse("1:HOLDING:0:DOUBLE:DCBA");
        assertEquals(4, point.count());
        assertEquals(ModbusPoint.DataType.DOUBLE, point.dataType());
        assertEquals(ModbusPoint.ByteOrder.DCBA, point.byteOrder());
    }

    @Test
    void parsesInt32() throws Exception {
        ModbusPoint point = ModbusPoint.parse("1:HOLDING:0:INT32:ABCD");
        assertEquals(ModbusPoint.DataType.INT32, point.dataType());
        assertEquals(2, point.count());
    }

    @Test
    void rejectsUnknownByteOrderWithHint() {
        Exception error = assertThrows(Exception.class, () -> ModbusPoint.parse("1:HOLDING:6:FLOAT32:ABDC"));
        assertTrue(error.getMessage().contains("ABDC"));
        assertTrue(error.getMessage().contains("ABCD"));
        assertTrue(error.getMessage().contains("BADC"));
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(Exception.class, () -> ModbusPoint.parse("bad"));
    }
}
