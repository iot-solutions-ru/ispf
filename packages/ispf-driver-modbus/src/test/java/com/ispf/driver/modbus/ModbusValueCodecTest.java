package com.ispf.driver.modbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModbusValueCodecTest {

    @Test
    void decodesFloat32Badc() {
        // IEEE 12.34 = 0x414570A4 → BADC wire words 0x4541, 0xA470
        ModbusPoint point = new ModbusPoint(
                1,
                ModbusPoint.RegisterType.HOLDING,
                0,
                2,
                ModbusPoint.DataType.FLOAT32,
                ModbusPoint.ByteOrder.BADC
        );
        double value = ModbusValueCodec.decodeValue(new int[] { 0x4541, 0xA470 }, point);
        assertEquals(12.34, value, 1e-5);
    }

    @Test
    void decodesFloat32Abcd() {
        ModbusPoint point = new ModbusPoint(
                1,
                ModbusPoint.RegisterType.HOLDING,
                0,
                2,
                ModbusPoint.DataType.FLOAT32,
                ModbusPoint.ByteOrder.ABCD
        );
        double value = ModbusValueCodec.decodeValue(new int[] { 0x4145, 0x70A4 }, point);
        assertEquals(12.34, value, 1e-5);
    }

    @Test
    void decodesInt16Signed() {
        ModbusPoint point = new ModbusPoint(
                1,
                ModbusPoint.RegisterType.HOLDING,
                0,
                1,
                ModbusPoint.DataType.INT16,
                ModbusPoint.ByteOrder.ABCD
        );
        assertEquals(-2.0, ModbusValueCodec.decodeValue(new int[] { 0xFFFE }, point), 0.0);
    }
}
