package com.ispf.driver.cclinkie;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.cclinkie.codec.CcLinkIeSession;
import com.ispf.driver.cclinkie.codec.Slmp3eCodec;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CC-Link IE driver — MELSEC SLMP / MC Protocol 3E binary over TCP (default port {@code 5007}).
 * <p>
 * Speaks the SLMP 3E request layout that CC-Link IE gateways use for D/R/W devices.
 * This is not the CC-Link RS-485 ASIC, not an IE Field ASIC, and not a CLPA field stack.
 * Point forms: {@code D100}, {@code R0}, {@code W0}, {@code dev:D100}.
 * <p>
 * Clean-room ISPF code, Apache-2.0 — JDK sockets only.
 */
public class CcLinkIeDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("ccLinkIeValue")
            .field("value", FieldType.DOUBLE)
            .field("kind", FieldType.STRING)
            .field("address", FieldType.LONG)
            .field("point", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "cc-link-ie",
            "CC-Link IE SLMP 3E Driver",
            "1.0.0",
            "MELSEC SLMP 3E binary (MC protocol) over TCP for D/R/W via CC-Link IE gateway;"
                    + " not CC-Link RS-485 ASIC / IE Field ASIC / CLPA stack",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5007",
                    "timeoutMs", "3000",
                    "networkNo", "0",
                    "pcNo", "255",
                    "ioNo", "1023",
                    "stationNo", "0",
                    "monitoringTimer", "16"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5007;
    private int timeoutMs = 3000;
    private int networkNo = 0;
    private int pcNo = 0xFF;
    private int ioNo = 0x03FF;
    private int stationNo = 0;
    private int monitoringTimer = 0x0010;
    private CcLinkIeSession session;
    private final Map<String, CcLinkIePoint> points = new ConcurrentHashMap<>();

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
            case "networkNo" -> networkNo = Integer.parseInt(value.trim());
            case "pcNo" -> pcNo = Integer.parseInt(value.trim());
            case "ioNo" -> ioNo = Integer.parseInt(value.trim());
            case "stationNo" -> stationNo = Integer.parseInt(value.trim());
            case "monitoringTimer" -> monitoringTimer = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            session = new CcLinkIeSession(
                    host, port, timeoutMs, networkNo, pcNo, ioNo, stationNo, monitoringTimer);
            driverObject.log(DriverLogLevel.INFO,
                    "CC-Link IE SLMP 3E connected to " + host + ":" + port
                            + " (not RS-485 ASIC / IE Field ASIC / CLPA stack)");
        } catch (IOException e) {
            session = null;
            throw new DriverException("CC-Link IE connect failed for " + host + ":" + port, e);
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
            CcLinkIePoint point = CcLinkIePoint.parse(mapping);
            points.put(entry.getKey(), point);
            try {
                int word = session.readWord(point.deviceCode(), point.address());
                driverObject.updateVariable(entry.getKey(), toRecord(point, word));
            } catch (IOException e) {
                throw new DriverException("CC-Link IE read failed for " + mapping, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        ensureConnected();
        CcLinkIePoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        int word = (int) Math.round(extractNumeric(value)) & 0xFFFF;
        try {
            session.writeWord(point.deviceCode(), point.address(), word);
            driverObject.updateVariable(pointId, toRecord(point, word));
        } catch (IOException e) {
            throw new DriverException("CC-Link IE write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(CcLinkIePoint point, int word) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", (double) word,
                "kind", point.kind(),
                "address", (long) point.address(),
                "point", point.display()
        ));
    }

    private static double extractNumeric(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            throw new IllegalArgumentException("CC-Link IE write requires a value");
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "raw")) {
            Object candidate = row.get(key);
            if (candidate instanceof Number number) {
                return number.doubleValue();
            }
            if (candidate != null) {
                return Double.parseDouble(String.valueOf(candidate).trim());
            }
        }
        throw new IllegalArgumentException("CC-Link IE write requires numeric value/raw");
    }

    private void ensureConnected() throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
    }

    /** Exposed for literal frame tests. */
    public static byte[] buildReadD100ReferenceFrame() {
        return Slmp3eCodec.buildReadD100Reference();
    }
}
