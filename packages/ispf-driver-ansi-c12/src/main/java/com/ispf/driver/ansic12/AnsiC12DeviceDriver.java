package com.ispf.driver.ansic12;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.ansic12.codec.AnsiC12LabClient;
import com.ispf.driver.ansic12.codec.AnsiC12LabCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ANSI C12.18 / C12.22 <strong>meter lab</strong> driver ({@code ansi-c12}).
 * <p>
 * Clean-room Apache-2.0 TCP subset on port 1153: logon + read standard table
 * (e.g. Table 1 / ST-1 identification). Optional table write is supported for lab
 * exercise. This is <strong>not</strong> a certified C12.22 network relay or vendor
 * meter SDK — see {@link AnsiC12LabCodec}.
 * <p>
 * Point mapping: {@code table:1}, {@code ST1}, or {@code 1}.
 */
public class AnsiC12DeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ansi-c12",
            "ANSI C12 Lab Driver",
            "0.1.0",
            "ANSI C12.18/C12.22 meter lab over TCP: logon + standard table read/write;"
                    + " not a certified C12.22 relay",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "1153",
                    "timeoutMs", "3000",
                    "user", "ISPF",
                    "password", ""
            ),
            null,
            Set.of("read", "write")
    );

    private static final DataSchema TABLE_SCHEMA = DataSchema.builder("ansiC12Table")
            .field("value", FieldType.STRING)
            .field("hex", FieldType.STRING)
            .field("tableId", FieldType.LONG)
            .field("label", FieldType.STRING)
            .build();

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 1153;
    private int timeoutMs = 3000;
    private String user = "ISPF";
    private String password = "";
    private AnsiC12LabClient client;
    private final Map<String, AnsiC12Point> points = new ConcurrentHashMap<>();

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
        if (value == null) {
            return;
        }
        switch (key) {
            case "host" -> {
                if (!value.isBlank()) {
                    host = value.trim();
                }
            }
            case "port" -> {
                if (!value.isBlank()) {
                    port = Integer.parseInt(value.trim());
                }
            }
            case "timeoutMs" -> {
                if (!value.isBlank()) {
                    timeoutMs = Integer.parseInt(value.trim());
                }
            }
            case "user" -> user = value;
            case "password" -> password = value;
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        disconnect();
        try {
            client = new AnsiC12LabClient(host, port, timeoutMs);
            client.logon(user, password);
            driverObject.log(DriverLogLevel.INFO,
                    "ANSI C12-lab logged on to " + host + ":" + port);
        } catch (IOException e) {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {
                    // best effort
                }
                client = null;
            }
            throw new DriverException("ANSI C12-lab connect/logon failed for " + host + ":" + port, e);
        }
    }

    @Override
    public void disconnect() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // best effort
            }
            client = null;
        }
        points.clear();
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isLoggedOn();
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            AnsiC12Point point = AnsiC12Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            try {
                byte[] data = client.readTable(point.tableId());
                driverObject.updateVariable(entry.getKey(), toRecord(point, data));
            } catch (IOException e) {
                throw new DriverException("ANSI C12-lab read failed for " + point, e);
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        AnsiC12Point point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown point: " + pointId + " (read it first)");
        }
        byte[] data = extractBytes(value);
        try {
            client.writeTable(point.tableId(), data);
            driverObject.updateVariable(pointId, toRecord(point, data));
        } catch (IOException e) {
            throw new DriverException("ANSI C12-lab write failed for " + point, e);
        }
    }

    private static DataRecord toRecord(AnsiC12Point point, byte[] data) {
        return DataRecord.single(TABLE_SCHEMA, Map.of(
                "value", AnsiC12LabClient.asciiOrHex(data),
                "hex", HexFormat.of().formatHex(data),
                "tableId", (long) point.tableId(),
                "label", point.label()
        ));
    }

    private static byte[] extractBytes(DataRecord value) throws DriverException {
        Object hex = value.firstRow().get("hex");
        if (hex != null && !String.valueOf(hex).isBlank()) {
            try {
                return HexFormat.of().parseHex(String.valueOf(hex).trim());
            } catch (IllegalArgumentException e) {
                throw new DriverException("ANSI C12-lab write hex invalid: " + hex, e);
            }
        }
        Object raw = value.firstRow().get("value");
        if (raw == null) {
            raw = value.firstRow().get("raw");
        }
        if (raw == null) {
            throw new DriverException("ANSI C12-lab write requires value or hex field");
        }
        return String.valueOf(raw).getBytes(StandardCharsets.US_ASCII);
    }
}
