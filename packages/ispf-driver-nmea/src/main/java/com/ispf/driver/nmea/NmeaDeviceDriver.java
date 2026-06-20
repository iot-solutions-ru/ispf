package com.ispf.driver.nmea;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NMEA GPS driver — reads TCP NMEA feed and parses sentence types (GPGGA, GPRMC, etc.).
 */
public class NmeaDeviceDriver implements DeviceDriver {

    private static final DataSchema SENTENCE_SCHEMA = DataSchema.builder("nmeaSentence")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "nmea",
            "NMEA GPS Driver",
            "0.1.0",
            "Reads NMEA 0183 sentences from a TCP GPS feed and maps parsed fields to JSON",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "10110",
                    "pollIntervalMs", "1000"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 10110;
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private volatile boolean connected;

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
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "NMEA driver ready for " + host + ":" + port);
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        points.clear();
        List<String> lines = readLines();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String sentenceType = entry.getValue();
            if (sentenceType == null || sentenceType.isBlank()) {
                throw new DriverException("NMEA sentence type is blank for " + entry.getKey());
            }
            points.put(entry.getKey(), sentenceType);
            driverObject.updateVariable(entry.getKey(), parseLastMatching(lines, sentenceType));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("NMEA driver is read-only in v0.1");
    }

    private List<String> readLines() throws DriverException {
        List<String> lines = new ArrayList<>();
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(3000);
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() >= 100) {
                    break;
                }
            }
        } catch (Exception e) {
            throw new DriverException("NMEA TCP read failed", e);
        }
        return lines;
    }

    private static DataRecord parseLastMatching(List<String> lines, String sentenceType) {
        String target = sentenceType.trim().toUpperCase(Locale.ROOT);
        String lastRaw = "";
        Map<String, String> lastFields = Map.of();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Map<String, String> fields = NmeaParser.parseSentenceFields(line);
            String type = fields.getOrDefault("type", "");
            if (type.startsWith(target) || type.equals(target)) {
                lastRaw = line.trim();
                lastFields = fields;
            }
        }
        String json = NmeaParser.toJson(lastFields);
        return DataRecord.single(SENTENCE_SCHEMA, Map.of(
                "value", json,
                "raw", lastRaw
        ));
    }
}
