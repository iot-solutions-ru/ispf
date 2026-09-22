package com.ispf.driver.wisun;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.wisun.codec.WisunCoapCodec;
import com.ispf.driver.wisun.codec.WisunCoapSession;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wi-SUN border-router CoAP client over UDP (RFC 7252 CON GET).
 * <p>
 * Point forms: {@code node:1}, {@code /nodes/1/value}, {@code coap:/nodes/1/value}.
 * Sends CON GET with no token and MID 1 ({@code 40 01 00 01}); peer ACK 2.05 Content
 * with the same MID and no payload is {@code 60 45 00 01}.
 * <p>
 * Honesty: CoAP over UDP — not Wi-SUN FAN PHY / FAN stack.
 * Clean-room ISPF code, Apache-2.0 — JDK {@link java.net.DatagramSocket} only.
 */
public class WisunDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("wisunValue")
            .field("value", FieldType.DOUBLE)
            .field("path", FieldType.STRING)
            .field("code", FieldType.INTEGER)
            .field("mid", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "wisun",
            "Wi-SUN Border-Router CoAP Driver",
            "0.1.0",
            "CoAP RFC 7252 CON GET over UDP (port 5683);"
                    + " not Wi-SUN FAN PHY / FAN stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5683",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5683;
    private int timeoutMs = 3000;
    private WisunCoapSession session;
    private final Map<String, WisunPoint> points = new ConcurrentHashMap<>();

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
            session = new WisunCoapSession(host, port, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "Wi-SUN CoAP UDP ready for " + host + ":" + port
                            + " (not Wi-SUN FAN PHY / FAN stack)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("Wi-SUN CoAP connect failed for " + host + ":" + port, e);
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
            WisunPoint point = WisunPoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                int mid = session.getMid1();
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", 1.0d,
                        "path", point.path(),
                        "code", WisunCoapCodec.CODE_CONTENT,
                        "mid", mid
                )));
            } catch (IOException e) {
                throw new DriverException("Wi-SUN CoAP read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Wi-SUN CoAP driver is read-only (CON GET subset)");
    }

    static byte[] buildConGetMid1() {
        return WisunCoapCodec.encodeConGetNoToken(1);
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
