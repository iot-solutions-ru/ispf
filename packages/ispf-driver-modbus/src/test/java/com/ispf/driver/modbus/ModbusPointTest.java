package com.ispf.driver.modbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModbusPointTest {

    @Test
    void parsesHoldingRegisterMapping() throws Exception {
        ModbusPoint point = ModbusPoint.parse("1:HOLDING:10");
        assertEquals(1, point.slaveId());
        assertEquals(ModbusPoint.RegisterType.HOLDING, point.type());
        assertEquals(10, point.address());
        assertEquals(1, point.count());
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(Exception.class, () -> ModbusPoint.parse("bad"));
    }
}
