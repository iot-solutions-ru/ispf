package com.ispf.driver.thread;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.thread.codec.SpinelHdlcCodec;
import com.ispf.driver.thread.codec.SpinelHdlcSession;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread NCP/RCP driver — OpenThread Spinel HDLC over TCP (not 802.15.4 RF).
 * <p>
 * Point forms: {@code reset}, {@code cmd:reset}. {@code readPoints} sends Spinel
 * {@code CMD_RESET} framed as {@code 7E 80 01 02 92 7E}.
 * <p>
 * Honesty: Spinel HDLC host bridge — not 802.15.4 Thread radio / RCP silicon.
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class ThreadDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("threadValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("point", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "thread",
            "Thread Spinel HDLC Driver",
            "0.1.0",
            "OpenThread Spinel HDLC over TCP (CMD_RESET 7E 80 01 02 92 7E);"
                    + " not 802.15.4 Thread radio / RCP silicon",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "8081",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 8081;
    private int timeoutMs = 3000;
    private SpinelHdlcSession session;
    private final Map<String, ThreadPoint> points = new ConcurrentHashMap<>();

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
            session = new SpinelHdlcSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "Thread Spinel HDLC connected to " + host + ":" + port
                            + " (not 802.15.4 Thread radio / RCP)");
        } catch (IOException e) {
            session = null;
            throw new DriverException(
                    "Thread Spinel connect failed for " + host + ":" + port, e);
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
            ThreadPoint point = ThreadPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                byte[] frame = session.sendCmdReset();
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", 1.0d,
                        "kind", point.kindToken(),
                        "point", point.display(),
                        "raw", toHex(frame)
                )));
            } catch (IOException e) {
                throw new DriverException("Thread Spinel read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Thread Spinel driver is read-only (CMD_RESET subset)");
    }

    static byte[] buildCmdResetFrame() {
        return SpinelHdlcCodec.encodeCmdReset();
    }

    static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }
}
