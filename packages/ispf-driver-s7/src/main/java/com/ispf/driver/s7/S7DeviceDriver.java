package com.ispf.driver.s7;

import com.github.s7connector.api.S7Connector;
import com.github.s7connector.api.factory.S7ConnectorFactory;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Siemens S7 driver — reads PLC data blocks via s7connector.
 * <p>
 * Point mapping: {@code area:dbNumber:offset:type} e.g. {@code DB:1:0:REAL}.
 */
public class S7DeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "s7",
            "Siemens S7 Driver",
            "0.1.0",
            "Polls Siemens S7 PLCs over ISO-on-TCP and maps DB/register values to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "rack", "0",
                    "slot", "2",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "1000"
            )
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("s7Value")
            .field("value", FieldType.DOUBLE)
            .field("raw", FieldType.LONG)
            .build();

    private static final DataSchema BOOL_SCHEMA = DataSchema.builder("s7Bool")
            .field("value", FieldType.BOOLEAN)
            .build();

    private DriverObject driverObject;
    private S7Connector connector;
    private String host = "127.0.0.1";
    private int rack = 0;
    private int slot = 2;
    private int timeoutMs = 3000;
    private final Map<String, S7Point> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        readConfig("host", value -> host = value);
        readConfig("rack", value -> rack = Integer.parseInt(value));
        readConfig("slot", value -> slot = Integer.parseInt(value));
        readConfig("timeoutMs", value -> timeoutMs = Integer.parseInt(value));
    }

    @Override
    public void connect() throws DriverException {
        try {
            connector = S7ConnectorFactory
                    .buildTCPConnector()
                    .withHost(host)
                    .withRack(rack)
                    .withSlot(slot)
                    .withTimeout(timeoutMs)
                    .build();
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "Connected to S7 PLC " + host + " rack=" + rack + " slot=" + slot);
        } catch (Exception e) {
            connected = false;
            connector = null;
            throw new DriverException("S7 connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (connector != null) {
            try {
                connector.close();
            } catch (Exception ignored) {
                // best effort
            }
            connector = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && connector != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            S7Point point = S7Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("S7 write not implemented");
    }

    private DataRecord readPoint(S7Point point) throws DriverException {
        try {
            int length = point.dataType().byteLength();
            byte[] data = connector.read(point.area(), point.dbNumber(), length, point.offset());
            return decodeValue(point.dataType(), data);
        } catch (Exception e) {
            throw new DriverException("S7 read failed at " + point, e);
        }
    }

    private static DataRecord decodeValue(S7Point.S7DataType type, byte[] data) throws DriverException {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        return switch (type) {
            case BOOL -> DataRecord.single(BOOL_SCHEMA, Map.of("value", (data[0] & 0x01) != 0));
            case BYTE, USINT -> DataRecord.single(VALUE_SCHEMA, Map.of(
                    "raw", (long) (data[0] & 0xFF),
                    "value", (double) (data[0] & 0xFF)
            ));
            case SINT -> DataRecord.single(VALUE_SCHEMA, Map.of(
                    "raw", (long) data[0],
                    "value", (double) data[0]
            ));
            case INT -> {
                short raw = buffer.getShort();
                yield DataRecord.single(VALUE_SCHEMA, Map.of("raw", (long) raw, "value", (double) raw));
            }
            case UINT, WORD -> {
                int raw = buffer.getShort() & 0xFFFF;
                yield DataRecord.single(VALUE_SCHEMA, Map.of("raw", (long) raw, "value", (double) raw));
            }
            case DINT -> {
                int raw = buffer.getInt();
                yield DataRecord.single(VALUE_SCHEMA, Map.of("raw", (long) raw, "value", (double) raw));
            }
            case UDINT, DWORD -> {
                long raw = buffer.getInt() & 0xFFFFFFFFL;
                yield DataRecord.single(VALUE_SCHEMA, Map.of("raw", raw, "value", (double) raw));
            }
            case REAL -> {
                float raw = buffer.getFloat();
                yield DataRecord.single(VALUE_SCHEMA, Map.of("raw", (long) Float.floatToRawIntBits(raw), "value", (double) raw));
            }
            case LREAL -> {
                double raw = buffer.getDouble();
                yield DataRecord.single(VALUE_SCHEMA, Map.of("raw", Double.doubleToRawLongBits(raw), "value", raw));
            }
        };
    }

    private void readConfig(String name, java.util.function.Consumer<String> consumer) {
        driverObject.getVariable(name).ifPresent(record -> {
            Object raw = record.firstRow().get("raw");
            if (raw == null) {
                raw = record.firstRow().get("value");
            }
            if (raw != null) {
                consumer.accept(raw.toString());
            }
        });
    }
}
