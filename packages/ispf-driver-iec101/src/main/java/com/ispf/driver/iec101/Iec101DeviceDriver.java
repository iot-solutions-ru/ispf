package com.ispf.driver.iec101;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.iec101.codec.Iec101LabSession;
import com.ispf.driver.iec101.codec.Iec101LabTypes;
import com.ispf.driver.iec101.codec.Iec101LabValue;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IEC 60870-5-101 TCP <strong>lab</strong> driver ({@code iec101}).
 * <p>
 * Clean-room Apache-2.0 codec — not a full balanced serial IEC 101 stack.
 * Uses simplified APCI+ASDU framing over TCP (port 2404 by default) inspired by
 * the ISPF IEC 104 layout so CI can exercise interrogation without FT1.2.
 * See {@link com.ispf.driver.iec101.codec.Iec101LabCodec} for 101-vs-104 differences.
 * <p>
 * Minimum lab behaviour: connect (STARTDT handshake) + general interrogation
 * ({@code C_IC_NA_1}) returning {@code M_ME_NC_1} / {@code M_SP_NA_1}. Optional
 * writes via {@code C_SC_NA_1} / {@code C_SE_NC_1}.
 * <p>
 * Point mapping: IOA, {@code M_ME_NC_1:IOA}, {@code M_SP_NA_1:IOA}, or {@code IOA:FLOAT}/{@code IOA:BOOL}.
 * No OpenMUC / GPL IEC libraries.
 */
public class Iec101DeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "iec101",
            "IEC 60870-5-101 Lab Driver",
            "0.1.0",
            "IEC101-lab over TCP: APCI+ASDU subset (C_IC_NA_1 / M_ME_NC_1 / M_SP_NA_1);"
                    + " not full balanced serial IEC 60870-5-101",
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

    private static final DataSchema FLOAT_SCHEMA = DataSchema.builder("iec101Float")
            .field("value", FieldType.DOUBLE)
            .field("quality", FieldType.STRING)
            .field("ioa", FieldType.LONG)
            .field("typeId", FieldType.LONG)
            .build();

    private static final DataSchema BOOL_SCHEMA = DataSchema.builder("iec101Bool")
            .field("value", FieldType.BOOLEAN)
            .field("quality", FieldType.STRING)
            .field("ioa", FieldType.LONG)
            .field("typeId", FieldType.LONG)
            .build();

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 2404;
    private int commonAddress = 1;
    private int timeoutMs = 3000;
    private Iec101LabSession session;
    private final Map<String, Iec101Point> points = new ConcurrentHashMap<>();
    private Map<Integer, Iec101LabValue> lastInterrogation = Map.of();

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
            session = new Iec101LabSession(host, port, commonAddress, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "IEC101-lab connected to " + host + ":" + port
                            + " (commonAddress=" + commonAddress + ")");
        } catch (IOException e) {
            session = null;
            throw new DriverException("IEC101-lab connect failed for " + host + ":" + port, e);
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
            throw new DriverException("IEC101-lab interrogation failed", e);
        }
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Iec101Point point = Iec101Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            Iec101LabValue value = lastInterrogation.get(point.ioa());
            if (value == null) {
                throw new DriverException("IEC101-lab IOA " + point.ioa() + " not present in interrogation");
            }
            driverObject.updateVariable(entry.getKey(), toRecord(point, value));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        Iec101Point point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        try {
            switch (point.kind()) {
                case SINGLE_POINT -> {
                    boolean on = extractBoolean(value);
                    session.writeSingleCommand(point.ioa(), on);
                    driverObject.updateVariable(pointId, DataRecord.single(BOOL_SCHEMA, Map.of(
                            "value", on,
                            "quality", "GOOD",
                            "ioa", (long) point.ioa(),
                            "typeId", (long) Iec101LabTypes.C_SC_NA_1
                    )));
                }
                case MEASURED_FLOAT -> {
                    float numeric = (float) extractDouble(value);
                    session.writeSetpointFloat(point.ioa(), numeric);
                    driverObject.updateVariable(pointId, DataRecord.single(FLOAT_SCHEMA, Map.of(
                            "value", (double) numeric,
                            "quality", "GOOD",
                            "ioa", (long) point.ioa(),
                            "typeId", (long) Iec101LabTypes.C_SE_NC_1
                    )));
                }
            }
        } catch (IOException e) {
            throw new DriverException("IEC101-lab write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(Iec101Point point, Iec101LabValue value) throws DriverException {
        return switch (point.kind()) {
            case SINGLE_POINT -> {
                if (value.typeId() != Iec101LabTypes.M_SP_NA_1) {
                    throw new DriverException("IOA " + point.ioa() + " is not M_SP_NA_1");
                }
                yield DataRecord.single(BOOL_SCHEMA, Map.of(
                        "value", value.bool(),
                        "quality", value.quality(),
                        "ioa", (long) value.ioa(),
                        "typeId", (long) value.typeId()
                ));
            }
            case MEASURED_FLOAT -> {
                if (value.typeId() != Iec101LabTypes.M_ME_NC_1) {
                    throw new DriverException("IOA " + point.ioa() + " is not M_ME_NC_1");
                }
                yield DataRecord.single(FLOAT_SCHEMA, Map.of(
                        "value", value.numeric(),
                        "quality", value.quality(),
                        "ioa", (long) value.ioa(),
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
            throw new DriverException("IEC101-lab write requires boolean value");
        }
        String text = String.valueOf(raw).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "on".equals(text);
    }

    private static double extractDouble(DataRecord value) throws DriverException {
        Object raw = value.firstRow().get("value");
        if (raw == null) {
            raw = value.firstRow().get("raw");
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw == null) {
            throw new DriverException("IEC101-lab write requires numeric value");
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new DriverException("IEC101-lab write requires numeric value: " + raw, e);
        }
    }
}
