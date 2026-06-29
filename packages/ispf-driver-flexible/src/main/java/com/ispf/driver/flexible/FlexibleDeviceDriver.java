package com.ispf.driver.flexible;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Flexible driver — TCP/UDP request/response with legacy regex mode or exchange pipeline.
 */
public class FlexibleDeviceDriver implements DeviceDriver {

    private static final DataSchema RESPONSE_SCHEMA = DataSchema.builder("flexibleResponse")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .field("bytesRead", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "flexible",
            "Flexible Protocol Driver",
            "0.2.0",
            "TCP/UDP request/response with optional framed I/O, checksum verification, and extractors",
            "ISPF",
            Map.of(
                    "protocol", "TCP",
                    "host", "127.0.0.1",
                    "port", "5000",
                    "timeoutMs", "5000",
                    "encoding", "utf8",
                    "readMode", "idle",
                    "readMaxBytes", "8192",
                    "checksumAlgorithm", "none"
            )
    );

    private DriverObject driverObject;
    private String protocol = "TCP";
    private String host = "127.0.0.1";
    private int port = 5000;
    private int timeoutMs = 5000;
    private String encoding = "utf8";
    private String readMode = "idle";
    private int readUntilDelimiter = 0x03;
    private int readMaxBytes = 8192;
    private String checksumAlgorithm = "none";
    private String checksumMarker = "&&";
    private int checksumLength = 4;
    private final Map<String, String> configuration = new HashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        configuration.clear();
        driverObject.configuration().forEach((key, value) -> {
            configuration.put(key, value);
            applyConfig(key, value);
        });
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "protocol" -> protocol = value.trim().toUpperCase(Locale.ROOT);
            case "host" -> host = value.trim();
            case "port" -> port = Integer.parseInt(value.trim());
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "encoding" -> encoding = value.trim().toLowerCase(Locale.ROOT);
            case "readMode" -> readMode = value.trim().toLowerCase(Locale.ROOT);
            case "readUntilHex" -> readUntilDelimiter = Integer.parseInt(value.trim(), 16) & 0xFF;
            case "readMaxBytes" -> readMaxBytes = Integer.parseInt(value.trim());
            case "checksumAlgorithm" -> checksumAlgorithm = value.trim();
            case "checksumMarker" -> checksumMarker = value.trim();
            case "checksumLength" -> checksumLength = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "Flexible driver ready (" + protocol + " " + host + ":" + port
                        + ", encoding=" + encoding + ", readMode=" + readMode + ")");
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

        List<Map.Entry<String, FlexiblePoint>> legacyPoints = new ArrayList<>();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            if (FlexExchangePoint.isPipeline(entry.getValue())) {
                continue;
            }
            legacyPoints.add(Map.entry(entry.getKey(), FlexiblePoint.parse(entry.getValue())));
        }

        for (Map.Entry<String, FlexiblePoint> entry : legacyPoints) {
            driverObject.updateVariable(entry.getKey(), exchangeLegacy(entry.getValue()));
        }

        Map<String, List<Map.Entry<String, FlexExchangePoint>>> groups =
                FlexExchangePoint.groupByRequest(pointMappings, configuration);
        for (List<Map.Entry<String, FlexExchangePoint>> group : groups.values()) {
            if (group.isEmpty()) {
                continue;
            }
            FlexExchangePoint sample = group.get(0).getValue();
            byte[] request = sample.renderRequest(configuration);
            byte[] frame = sendAndReceive(request);
            String payload = resolvePayload(frame, sample.verifyChecksum());
            for (Map.Entry<String, FlexExchangePoint> entry : group) {
                FlexExchangePoint point = entry.getValue();
                String value = point.extractor().extract(payload);
                driverObject.updateVariable(entry.getKey(), pipelineRecord(payload, frame.length, value));
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Flexible driver is read-only");
    }

    private String resolvePayload(byte[] frame, boolean pointVerifyChecksum) throws DriverException {
        boolean verify = pointVerifyChecksum || shouldVerifyChecksum();
        if (!verify) {
            return FlexTemplate.asPrintable(frame);
        }
        return FlexChecksum.verifyAndPayload(frame, checksumAlgorithm, checksumMarker, checksumLength);
    }

    private boolean shouldVerifyChecksum() {
        return checksumAlgorithm != null
                && !checksumAlgorithm.isBlank()
                && !"none".equalsIgnoreCase(checksumAlgorithm);
    }

    private DataRecord pipelineRecord(String payload, int bytesRead, String value) {
        return DataRecord.single(RESPONSE_SCHEMA, Map.of(
                "value", value == null ? "" : value,
                "raw", payload == null ? "" : payload,
                "bytesRead", bytesRead
        ));
    }

    private DataRecord exchangeLegacy(FlexiblePoint point) throws DriverException {
        byte[] request = encodeLegacyRequest(point.request());
        byte[] frame = sendAndReceive(request);
        String raw = decodeLegacyResponse(frame);
        return toLegacyRecord(raw, point);
    }

    private byte[] sendAndReceive(byte[] request) throws DriverException {
        try {
            if ("UDP".equals(protocol)) {
                return exchangeUdpBytes(request);
            }
            return exchangeTcpBytes(request);
        } catch (IOException e) {
            throw new DriverException("Flexible exchange failed", e);
        }
    }

    private byte[] exchangeTcpBytes(byte[] request) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            out.write(request);
            out.flush();
            return FlexResponseReader.read(
                    socket.getInputStream(), readMode, readUntilDelimiter, readMaxBytes, timeoutMs);
        }
    }

    private byte[] exchangeUdpBytes(byte[] request) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            DatagramPacket send = new DatagramPacket(
                    request, request.length,
                    InetSocketAddress.createUnresolved(host, port));
            socket.send(send);
            byte[] buffer = new byte[readMaxBytes];
            DatagramPacket receive = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(receive);
            } catch (SocketTimeoutException e) {
                return new byte[0];
            }
            byte[] frame = new byte[receive.getLength()];
            System.arraycopy(buffer, 0, frame, 0, receive.getLength());
            return frame;
        }
    }

    private DataRecord toLegacyRecord(String raw, FlexiblePoint point) {
        String value = raw;
        if (point.responseRegex() != null) {
            Matcher matcher = point.responseRegex().matcher(raw);
            if (matcher.find() && matcher.groupCount() >= 1) {
                value = matcher.group(1);
            } else if (matcher.matches() && matcher.groupCount() >= 1) {
                value = matcher.group(1);
            } else {
                value = "";
            }
        }
        return DataRecord.single(RESPONSE_SCHEMA, Map.of(
                "value", value == null ? "" : value,
                "raw", raw == null ? "" : raw,
                "bytesRead", raw == null ? 0 : raw.length()
        ));
    }

    private byte[] encodeLegacyRequest(String request) {
        if ("hex".equals(encoding)) {
            return hexToBytes(request.replaceAll("\\s+", ""));
        }
        if ("escapes".equals(encoding)) {
            return FlexTemplate.render(request, configuration);
        }
        return request.getBytes(StandardCharsets.UTF_8);
    }

    private String decodeLegacyResponse(byte[] frame) {
        if ("hex".equals(encoding)) {
            return bytesToHex(frame, frame.length);
        }
        return FlexTemplate.asPrintable(frame);
    }

    static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string length");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    static String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
}
