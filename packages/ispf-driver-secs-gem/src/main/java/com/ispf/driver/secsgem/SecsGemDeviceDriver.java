package com.ispf.driver.secsgem;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.secsgem.codec.SecsGemSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SEMI E37 HSMS / SECS-II GEM driver ({@code secs-gem}).
 * <p>
 * TCP HSMS: Select.req/Select.rsp, then S1F13/S1F14, S1F1/S1F2, S2F13/S2F14 (VID),
 * S6F1 status, and optional S2F41 remote command writes. Not SECS-I serial.
 * <p>
 * Point mapping: {@code S1F1}, {@code status}, or {@code VID:100}. See {@link SecsGemPoint}.
 */
public class SecsGemDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "secs-gem",
            "SECS/GEM HSMS Driver",
            "1.0.0",
            "SEMI E37 HSMS over TCP: Select.req/rsp, S1F1/S1F2, S1F13/S1F14,"
                    + " S2F13/S2F14 VID, S6F1 status, S2F41 RCMD; not SECS-I serial",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5000",
                    "sessionId", "0",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("secsGemString")
            .field("value", FieldType.STRING)
            .field("mdln", FieldType.STRING)
            .field("softrev", FieldType.STRING)
            .field("point", FieldType.STRING)
            .build();

    private static final DataSchema NUMERIC_SCHEMA = DataSchema.builder("secsGemNumeric")
            .field("value", FieldType.DOUBLE)
            .field("vid", FieldType.LONG)
            .field("point", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5000;
    private int sessionId;
    private int timeoutMs = 3000;
    private SecsGemSession session;
    private final Map<String, SecsGemPoint> points = new ConcurrentHashMap<>();

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
            case "sessionId", "deviceId" -> sessionId = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            session = new SecsGemSession(host, port, sessionId, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "HSMS connected to " + host + ":" + port + " (sessionId=" + sessionId + ")");
        } catch (IOException e) {
            session = null;
            throw new DriverException("HSMS connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
                // best effort
            }
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
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        Map<String, SecsGemPoint> parsed = new LinkedHashMap<>();
        List<Long> vids = new ArrayList<>();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            SecsGemPoint point = SecsGemPoint.parse(entry.getValue());
            parsed.put(entry.getKey(), point);
            points.put(entry.getKey(), point);
            if (point.kind() == SecsGemPoint.Kind.VID) {
                vids.add(point.vid());
            }
        }

        Map<Long, Double> vidValues = Map.of();
        if (!vids.isEmpty()) {
            try {
                vidValues = session.readVids(vids);
            } catch (IOException e) {
                throw new DriverException("HSMS S2F13/S2F14 failed", e);
            }
        }

        for (Map.Entry<String, SecsGemPoint> entry : parsed.entrySet()) {
            String pointId = entry.getKey();
            SecsGemPoint point = entry.getValue();
            try {
                switch (point.kind()) {
                    case S1F1 -> {
                        Map<String, String> online = session.areYouThere();
                        driverObject.updateVariable(pointId, DataRecord.single(STRING_SCHEMA, Map.of(
                                "value", online.getOrDefault("online", "true"),
                                "mdln", online.getOrDefault("mdln", ""),
                                "softrev", online.getOrDefault("softrev", ""),
                                "point", "S1F1"
                        )));
                    }
                    case STATUS -> {
                        String status = session.readStatus();
                        driverObject.updateVariable(pointId, DataRecord.single(STRING_SCHEMA, Map.of(
                                "value", status,
                                "mdln", "",
                                "softrev", "",
                                "point", "status"
                        )));
                    }
                    case VID -> {
                        Double value = vidValues.get(point.vid());
                        if (value == null) {
                            throw new DriverException("HSMS VID " + point.vid() + " missing in S2F14");
                        }
                        driverObject.updateVariable(pointId, DataRecord.single(NUMERIC_SCHEMA, Map.of(
                                "value", value,
                                "vid", point.vid(),
                                "point", "VID:" + point.vid()
                        )));
                    }
                }
            } catch (IOException e) {
                throw new DriverException("HSMS read failed for " + pointId, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String rcmd = extractString(value);
        if (rcmd.isBlank()) {
            throw new DriverException("HSMS S2F41 requires non-blank RCMD value");
        }
        try {
            int hcack = session.sendRemoteCommand(rcmd);
            driverObject.updateVariable(pointId, DataRecord.single(STRING_SCHEMA, Map.of(
                    "value", rcmd,
                    "mdln", "",
                    "softrev", "HCACK=" + hcack,
                    "point", "S2F41"
            )));
            if (hcack != 0) {
                throw new DriverException("HSMS S2F41 HCACK=" + hcack + " for RCMD=" + rcmd);
            }
        } catch (IOException e) {
            throw new DriverException("HSMS S2F41 failed for " + pointId, e);
        }
    }

    private static String extractString(DataRecord value) throws DriverException {
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("HSMS write requires a value");
        }
        Object raw = value.firstRow().get("value");
        if (raw == null) {
            raw = value.firstRow().get("rcmd");
        }
        if (raw == null) {
            raw = value.firstRow().get("raw");
        }
        if (raw == null) {
            throw new DriverException("HSMS write requires value/rcmd");
        }
        return String.valueOf(raw).trim();
    }
}
