package com.ispf.driver.iec101;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.iec101.codec.Iec101Session;
import com.ispf.driver.iec101.codec.Iec101Types;
import com.ispf.driver.iec101.codec.Iec101Value;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IEC 60870-5-101 unbalanced primary over TCP ({@code iec101}).
 * <p>
 * Wire format is FT1.2: reset of remote link, then user data with confirm.
 * Structure sizes are COT 1 byte, common address 2 bytes, IOA 2 bytes, link address 1 byte.
 * Supported ASDUs: {@code C_IC_NA_1}, {@code M_ME_NC_1}, {@code M_SP_NA_1},
 * {@code C_SC_NA_1}, {@code C_SE_NC_1}. Balanced mode and file transfer are outside this driver.
 * <p>
 * Point mapping: IOA, {@code M_ME_NC_1:IOA}, {@code M_SP_NA_1:IOA}, or {@code IOA:FLOAT}/{@code IOA:BOOL}.
 */
public class Iec101DeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "iec101",
            "IEC 60870-5-101 Driver",
            "1.0.0",
            "IEC 60870-5-101 unbalanced FT1.2 over TCP: C_IC_NA_1, M_ME_NC_1, M_SP_NA_1,"
                    + " C_SC_NA_1, C_SE_NC_1. COT 1, common address 2, IOA 2, link address 1.",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "2404",
                    "linkAddress", "1",
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
    private int linkAddress = 1;
    private int commonAddress = 1;
    private int timeoutMs = 3000;
    private Iec101Session session;
    private final Map<String, Iec101Point> points = new ConcurrentHashMap<>();
    private Map<Integer, Iec101Value> lastInterrogation = Map.of();

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
            case "linkAddress" -> linkAddress = Integer.parseInt(value.trim());
            case "commonAddress" -> commonAddress = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            session = new Iec101Session(host, port, linkAddress, commonAddress, timeoutMs);
            driverObject.log(DriverLogLevel.INFO,
                    "IEC 101 connected to " + host + ":" + port
                            + " (linkAddress=" + linkAddress + ", commonAddress=" + commonAddress + ")");
        } catch (IOException e) {
            session = null;
            throw new DriverException("IEC 101 connect failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        if (session != null) {
            session.close();
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
            throw new DriverException("IEC 101 interrogation failed", e);
        }
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Iec101Point point = Iec101Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            Iec101Value value = lastInterrogation.get(point.ioa());
            if (value == null) {
                throw new DriverException("IEC 101 IOA " + point.ioa() + " not present in interrogation");
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
                            "typeId", (long) Iec101Types.C_SC_NA_1
                    )));
                }
                case MEASURED_FLOAT -> {
                    float numeric = (float) extractDouble(value);
                    session.writeSetpointFloat(point.ioa(), numeric);
                    driverObject.updateVariable(pointId, DataRecord.single(FLOAT_SCHEMA, Map.of(
                            "value", (double) numeric,
                            "quality", "GOOD",
                            "ioa", (long) point.ioa(),
                            "typeId", (long) Iec101Types.C_SE_NC_1
                    )));
                }
            }
        } catch (IOException e) {
            throw new DriverException("IEC 101 write failed for " + pointId, e);
        }
    }

    private static DataRecord toRecord(Iec101Point point, Iec101Value value) throws DriverException {
        return switch (point.kind()) {
            case SINGLE_POINT -> {
                if (value.typeId() != Iec101Types.M_SP_NA_1) {
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
                if (value.typeId() != Iec101Types.M_ME_NC_1) {
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
            throw new DriverException("IEC 101 write requires boolean value");
        }
        String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
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
            throw new DriverException("IEC 101 write requires numeric value");
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new DriverException("IEC 101 write requires numeric value: " + raw, e);
        }
    }
}
