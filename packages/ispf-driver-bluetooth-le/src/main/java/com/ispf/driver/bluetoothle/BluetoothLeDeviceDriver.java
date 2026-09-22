package com.ispf.driver.bluetoothle;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.bluetoothle.codec.BluetoothLeH4Session;
import com.ispf.driver.bluetoothle.codec.H4HciCodec;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bluetooth H4 HCI command driver over TCP (default port {@code 9999}).
 * <p>
 * Connect sends HCI_Reset ({@code 01 03 0C 00}) and expects Command Complete
 * {@code 04 0E 04 01 03 0C 00}. Point {@code bd_addr} / {@code hci:bd_addr} reads via
 * HCI_Read_BD_ADDR ({@code 01 09 10 00}).
 * <p>
 * Not a BLE radio and not a full GATT client. Clean-room ISPF code, Apache-2.0 —
 * JDK sockets only.
 */
public class BluetoothLeDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("bluetoothLeValue")
            .field("value", FieldType.STRING)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "bluetooth-le",
            "Bluetooth H4 HCI Driver",
            "0.1.0",
            "H4 HCI commands over TCP; not a BLE radio and not a full GATT client;"
                    + " HCI_Reset and HCI_Read_BD_ADDR",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "9999",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 9999;
    private int timeoutMs = 3000;
    private BluetoothLeH4Session session;
    private final Map<String, BluetoothLePoint> points = new ConcurrentHashMap<>();

    /** Handwritten H4 HCI_Reset: {@code 01 03 0C 00}. */
    public static byte[] encodeResetCommand() {
        return H4HciCodec.encodeReset();
    }

    /** Handwritten H4 HCI_Read_BD_ADDR: {@code 01 09 10 00}. */
    public static byte[] encodeReadBdAddrCommand() {
        return H4HciCodec.encodeReadBdAddr();
    }

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            session = new BluetoothLeH4Session(host, port, timeoutMs);
            session.reset();
            driverObject.log(DriverLogLevel.INFO,
                    "Bluetooth H4 HCI connected to " + host + ":" + port
                            + " (not a BLE radio / not a full GATT client)");
        } catch (IOException e) {
            if (session != null) {
                session.close();
                session = null;
            }
            throw new DriverException(
                    "Bluetooth H4 HCI connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        if (session != null) {
            session.close();
            session = null;
        }
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnected();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String mapping = entry.getValue() == null || entry.getValue().isBlank()
                    ? entry.getKey() : entry.getValue();
            BluetoothLePoint point = BluetoothLePoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                String bdAddr = session.readBdAddr();
                driverObject.updateVariable(entry.getKey(), toRecord(point, bdAddr));
            } catch (IOException e) {
                throw new DriverException(
                        "Bluetooth H4 HCI read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        BluetoothLePoint point = points.get(pointId);
        String display = point != null ? point.display() : pointId;
        throw new DriverException(
                "Bluetooth H4 HCI rejects writes (not a full GATT client): " + display);
    }

    private static DataRecord toRecord(BluetoothLePoint point, String bdAddr) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", bdAddr.toUpperCase(Locale.ROOT),
                "kind", "bd_addr",
                "point", point.display()
        ));
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
