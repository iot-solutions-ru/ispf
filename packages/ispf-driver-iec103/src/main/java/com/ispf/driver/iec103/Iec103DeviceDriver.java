package com.ispf.driver.iec103;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.iec103.codec.Iec103LabSession;
import com.ispf.driver.iec103.codec.Iec103LabTypes;
import com.ispf.driver.iec103.codec.Iec103LabValue;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IEC 60870-5-103 protection <strong>lab</strong> driver ({@code iec103}).
 * <p>
 * Clean-room Apache-2.0 codec — not a full serial FT1.2 IEC 103 stack.
 * Uses simplified APCI+ASDU framing over TCP (port 2404 by default) in the same
 * spirit as {@code ispf-driver-iec101}, with 103 type IDs (ASDU 1 / 9 / 40 lab,
 * GI 7/8, optional general command 20) and FUN/INF addressing.
 * <p>
 * Point mapping: {@code FUN:INF}, {@code ASDU:FUN:INF}, or {@code ASDUid:IOA}
 * where IOA packs {@code (FUN<<8)|INF}. See {@link Iec103Point}.
 * <p>
 * No OpenMUC / GPL IEC libraries.
 */
public class Iec103DeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "iec103",
            "IEC 60870-5-103 Lab Driver",
            "0.1.0",
            "IEC103-lab over TCP: APCI+ASDU subset (ASDU 7/8 GI, 1 status, 9/40 meas, 20 cmd);"
                    + " FUN/INF points; not full serial FT1.2 IEC 60870-5-103",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "2404",
                    "commonAddress", "1",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private static final DataSchema FLOAT_SCHEMA = DataSchema.builder("iec103Float")
            .field("value", FieldType.DOUBLE)
            .field("quality", FieldType.STRING)
            .field("fun", FieldType.LONG)
            .field("inf", FieldType.LONG)
            .field("typeId", FieldType.LONG)
            .build();

    private static final DataSchema BOOL_SCHEMA = DataSchema.builder("iec103Bool")
            .field("value", FieldType.BOOLEAN)
            .field("quality", FieldType.STRING)
            .field("fun", FieldType.LONG)
            .field("inf", FieldType.LONG)
            .field("typeId", FieldType.LONG)
            .build();

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 2404;
    private int commonAddress = 1;
    private int timeoutMs = 3000;
    private Iec103LabSession session;
    private final Map<String, Iec103Point> points = new ConcurrentHashMap<>();
    private Map<Integer, Iec103LabValue> lastInterrogation = Map.of();

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
            case "commonAddress" -> commonAddress = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            session = new Iec103LabSession(host, port, commonAddress, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "IEC103-lab connected to " + host + ":" + port
                            + " (commonAddress=" + commonAddress + ")");
        } catch (IOException e) {
            session = null;
            throw new DriverException("IEC103-lab connect failed for " + host + ":" + port, e);
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
        lastInterrogation = Map.of();
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
        try {
            lastInterrogation = session.generalInterrogation();
        } catch (IOException e) {
            throw new DriverException("IEC103-lab interrogation failed", e);
        }
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Iec103Point point = Iec103Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            Iec103LabValue value = lastInterrogation.get(point.packedIoa());
            if (value == null) {
                throw new DriverException("IEC103-lab FUN=" + point.fun()
                        + " INF=" + point.inf() + " not present in interrogation");
            }
            driverObject.updateVariable(entry.getKey(), toRecord(point, value));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        Iec103Point point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        if (point.kind() != Iec103Point.Kind.STATUS) {
            throw new DriverException("IEC103-lab write supports STATUS points only (general command ASDU 20)");
        }
        try {
            boolean on = extractBoolean(value);
            session.writeGeneralCommand(point.fun(), point.inf(), on);
            driverObject.updateVariable(pointId, DataRecord.single(BOOL_SCHEMA, Map.of(
                    "value", on,
                    "quality", "GOOD",
                    "fun", (long) point.fun(),
                    "inf", (long) point.inf(),
                    "typeId", (long) Iec103LabTypes.ASDU_GENERAL_COMMAND
            )));
        } catch (IOException e) {
            throw new DriverException("IEC103-lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(Iec103Point point, Iec103LabValue value) throws DriverException {
        return switch (point.kind()) {
            case STATUS -> {
                if (value.typeId() != Iec103LabTypes.ASDU_TIME_TAGGED) {
                    throw new DriverException("FUN=" + point.fun() + " INF=" + point.inf()
                            + " is not ASDU 1 status");
                }
                yield DataRecord.single(BOOL_SCHEMA, Map.of(
                        "value", value.bool(),
                        "quality", value.quality(),
                        "fun", (long) value.fun(),
                        "inf", (long) value.inf(),
                        "typeId", (long) value.typeId()
                ));
            }
            case MEASURED_FLOAT -> {
                if (value.typeId() != Iec103LabTypes.ASDU_LAB_MEAS_FLOAT
                        && value.typeId() != Iec103LabTypes.ASDU_MEASURANDS_II) {
                    throw new DriverException("FUN=" + point.fun() + " INF=" + point.inf()
                            + " is not measured ASDU 9/40");
                }
                yield DataRecord.single(FLOAT_SCHEMA, Map.of(
                        "value", value.numeric(),
                        "quality", value.quality(),
                        "fun", (long) value.fun(),
                        "inf", (long) value.inf(),
                        "typeId", (long) value.typeId()
                ));
            }
            case MEASURANDS_II -> {
                if (value.typeId() != Iec103LabTypes.ASDU_MEASURANDS_II) {
                    throw new DriverException("FUN=" + point.fun() + " INF=" + point.inf()
                            + " is not ASDU 9");
                }
                yield DataRecord.single(FLOAT_SCHEMA, Map.of(
                        "value", value.numeric(),
                        "quality", value.quality(),
                        "fun", (long) value.fun(),
                        "inf", (long) value.inf(),
                        "typeId", (long) value.typeId()
                ));
            }
        };
    }

    private static boolean extractBoolean(DataRecord value) throws DriverException {
        Object raw = value.firstRow().get("value");
        if (raw == null) {
            raw = value.firstRow().get("raw");
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw == null) {
            throw new DriverException("IEC103-lab write requires boolean value");
        }
        String text = String.valueOf(raw).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "on".equals(text);
    }
}
