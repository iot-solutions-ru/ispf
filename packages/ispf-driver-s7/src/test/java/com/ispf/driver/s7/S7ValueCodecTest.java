package com.ispf.driver.s7;

import com.github.s7connector.api.DaveArea;
import com.github.s7connector.api.S7Connector;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S7ValueCodecTest {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("s7Value")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    private static final DataSchema BOOL_SCHEMA = DataSchema.builder("s7Bool")
            .field("value", FieldType.BOOLEAN)
            .build();

    @Test
    void roundTripsRealValue() throws Exception {
        S7Point point = S7Point.parse("DB:1:0:REAL");
        MemoryS7Connector connector = new MemoryS7Connector();
        DataRecord written = DataRecord.single(VALUE_SCHEMA, Map.of("raw", 42L, "value", 42.5));

        byte[] encoded = S7DeviceDriver.encodeValue(point, written, connector);
        connector.write(point.area(), point.dbNumber(), point.offset(), encoded);

        DataRecord read = S7DeviceDriver.decodeValue(point.dataType(), connector.read(
                point.area(), point.dbNumber(), point.dataType().byteLength(), point.offset()));
        assertEquals(42.5, read.firstRow().get("value"));
    }

    @Test
    void boolWritePreservesOtherBits() throws Exception {
        S7Point point = S7Point.parse("DB:1:0:BOOL");
        MemoryS7Connector connector = new MemoryS7Connector();
        connector.write(DaveArea.DB, 1, 0, new byte[]{(byte) 0xFE});

        byte[] encoded = S7DeviceDriver.encodeValue(
                point,
                DataRecord.single(BOOL_SCHEMA, Map.of("value", true)),
                connector);
        connector.write(point.area(), point.dbNumber(), point.offset(), encoded);
        byte stored = connector.read(DaveArea.DB, 1, 1, 0)[0];
        assertEquals((byte) 0xFF, stored);

        encoded = S7DeviceDriver.encodeValue(
                point,
                DataRecord.single(BOOL_SCHEMA, Map.of("value", false)),
                connector);
        connector.write(point.area(), point.dbNumber(), point.offset(), encoded);
        stored = connector.read(DaveArea.DB, 1, 1, 0)[0];
        assertEquals((byte) 0xFE, stored);
    }

    @Test
    void decodeBoolReadsLeastSignificantBit() throws Exception {
        DataRecord record = S7DeviceDriver.decodeValue(S7Point.S7DataType.BOOL, new byte[]{(byte) 0x01});
        assertTrue((Boolean) record.firstRow().get("value"));

        record = S7DeviceDriver.decodeValue(S7Point.S7DataType.BOOL, new byte[]{(byte) 0xFE});
        assertFalse((Boolean) record.firstRow().get("value"));
    }

    private static final class MemoryS7Connector implements S7Connector {

        private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

        @Override
        public byte[] read(DaveArea area, int dbNumber, int length, int offset) {
            byte[] stored = storage.get(key(area, dbNumber, offset));
            if (stored == null) {
                return new byte[length];
            }
            byte[] copy = new byte[length];
            System.arraycopy(stored, 0, copy, 0, Math.min(length, stored.length));
            return copy;
        }

        @Override
        public void write(DaveArea area, int dbNumber, int offset, byte[] data) {
            storage.put(key(area, dbNumber, offset), data.clone());
        }

        @Override
        public void close() {
        }

        private static String key(DaveArea area, int dbNumber, int offset) {
            return area + ":" + dbNumber + ":" + offset;
        }
    }
}
