package com.ispf.driver.openadr;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenADR 2.0b VEN poll — one HTTP/1.1 POST of an {@code oadrPoll} document to a VTN URL path.
 * Clean-room JDK sockets only. Not a full VTN implementation and not an XML signature stack.
 */
public class OpenadrDeviceDriver implements DeviceDriver {

    static final String DEFAULT_POLL_PATH = "/OpenADR2/Simple/2.0b/OadrPoll";

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("openadrValue")
            .field("value", FieldType.STRING)
            .field("kind", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "openadr",
            "OpenADR Driver",
            "0.1.0",
            "OpenADR 2.0b oadrPoll VEN over HTTP/1.1 POST (not a full VTN or XML signature stack)",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "8080",
                    "pollPath", DEFAULT_POLL_PATH,
                    "venId", "ven-ispf-1",
                    "timeoutMs", "5000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 8080;
    private String pollPath = DEFAULT_POLL_PATH;
    private String venId = "ven-ispf-1";
    private int timeoutMs = 5000;
    private final Map<String, OpenadrPoint> points = new ConcurrentHashMap<>();
    private volatile OpenadrEventPayload lastPayload = OpenadrEventPayload.parse("");
    private volatile int lastStatusCode = -1;
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
            case "pollPath" -> pollPath = value.trim();
            case "venId" -> venId = value.trim();
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            case "vtnUrl" -> applyVtnUrl(value.trim());
            default -> { }
        }
    }

    private void applyVtnUrl(String url) {
        String rest = url;
        if (rest.regionMatches(true, 0, "http://", 0, 7)) {
            rest = rest.substring(7);
        } else if (rest.regionMatches(true, 0, "https://", 0, 8)) {
            rest = rest.substring(8);
        }
        int slash = rest.indexOf('/');
        String authority = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? DEFAULT_POLL_PATH : rest.substring(slash);
        int colon = authority.lastIndexOf(':');
        if (colon > 0) {
            host = authority.substring(0, colon);
            port = Integer.parseInt(authority.substring(colon + 1));
        } else {
            host = authority;
        }
        if (!path.isBlank()) {
            pollPath = path;
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "OpenADR VEN ready (host=" + host + ", port=" + port + ", venId=" + venId + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
        lastPayload = OpenadrEventPayload.parse("");
        lastStatusCode = -1;
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
        pollVtn();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            OpenadrPoint point = OpenadrPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), recordFor(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        OpenadrPoint point = points.get(pointId);
        if (point == null) {
            throw new DriverException("Unknown OpenADR point: " + pointId);
        }
        String opt = extractWriteValue(value);
        String eventId = lastPayload.eventId;
        if (eventId == null || eventId.isBlank()) {
            throw new DriverException("No active OpenADR event to acknowledge");
        }
        postCreatedEvent(eventId, opt);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", opt,
                "kind", point.kind(),
                "statusCode", lastStatusCode
        )));
    }

    private void pollVtn() throws DriverException {
        String body = buildOadrPollBody(venId);
        String request = buildOadrPollRequest(host, port, pollPath, body);
        HttpExchange response = exchange(request);
        lastStatusCode = response.status();
        if (lastStatusCode < 200 || lastStatusCode >= 300) {
            throw new DriverException("OpenADR poll failed: HTTP " + lastStatusCode);
        }
        lastPayload = OpenadrEventPayload.parse(response.body());
    }

    private void postCreatedEvent(String eventId, String optType) throws DriverException {
        String body = buildCreatedEventBody(venId, eventId, optType);
        String path = pollPath.toLowerCase(Locale.ROOT).endsWith("oadrpoll")
                ? pollPath.substring(0, pollPath.length() - "OadrPoll".length()) + "EiEvent"
                : pollPath;
        String request = buildHttpPost(host, port, path, body);
        HttpExchange response = exchange(request);
        lastStatusCode = response.status();
        if (lastStatusCode < 200 || lastStatusCode >= 300) {
            throw new DriverException("OpenADR createdEvent failed: HTTP " + lastStatusCode);
        }
    }

    private DataRecord recordFor(OpenadrPoint point) {
        return DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", lastPayload.valueFor(point),
                "kind", point.kind(),
                "statusCode", lastStatusCode
        ));
    }

    /**
     * Exact OpenADR 2.0b {@code oadrPoll} document body (no extra whitespace).
     */
    static String buildOadrPollBody(String venId) {
        return "<oadrPayload xmlns:oadr=\"http://openadr.org/oadr-2.0b/2012/07\">"
                + "<oadrSignedObject><oadrPoll>"
                + "<venID xmlns=\"http://docs.oasis-open.org/ns/energyinterop/201110\">"
                + escapeXml(venId)
                + "</venID></oadrPoll></oadrSignedObject></oadrPayload>";
    }

    /**
     * Exact HTTP/1.1 POST request line, headers, and body for an {@code oadrPoll}.
     */
    static String buildOadrPollRequest(String host, int port, String path, String body) {
        return buildHttpPost(host, port, path, body);
    }

    static String buildHttpPost(String host, int port, String path, String body) {
        int length = body.getBytes(StandardCharsets.UTF_8).length;
        return "POST " + path + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Content-Type: application/xml\r\n"
                + "Connection: close\r\n"
                + "Content-Length: " + length + "\r\n"
                + "\r\n"
                + body;
    }

    static String buildCreatedEventBody(String venId, String eventId, String optType) {
        String opt = optType == null || optType.isBlank() ? "optIn" : optType;
        return "<oadrPayload xmlns:oadr=\"http://openadr.org/oadr-2.0b/2012/07\">"
                + "<oadrSignedObject><oadrCreatedEvent>"
                + "<eiResponse xmlns=\"http://docs.oasis-open.org/ns/energyinterop/201110\">"
                + "<responseCode>200</responseCode></eiResponse>"
                + "<eventResponses xmlns=\"http://docs.oasis-open.org/ns/energyinterop/201110\">"
                + "<eventResponse><responseCode>200</responseCode>"
                + "<qualifiedEventID><eventID>" + escapeXml(eventId) + "</eventID></qualifiedEventID>"
                + "<optType>" + escapeXml(opt) + "</optType></eventResponse></eventResponses>"
                + "<venID xmlns=\"http://docs.oasis-open.org/ns/energyinterop/201110\">"
                + escapeXml(venId) + "</venID>"
                + "</oadrCreatedEvent></oadrSignedObject></oadrPayload>";
    }

    private HttpExchange exchange(String request) throws DriverException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return HttpExchange.parse(readAll(in));
        } catch (IOException e) {
            throw new DriverException("OpenADR poll failed for " + host + ":" + port + pollPath, e);
        }
    }

    private static String extractWriteValue(DataRecord value) throws DriverException {
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("OpenADR write requires a non-empty DataRecord");
        }
        Map<String, Object> row = value.firstRow();
        Object raw = row.get("value");
        if (raw == null) {
            raw = row.get("optType");
        }
        if (raw == null) {
            raw = row.get("raw");
        }
        return raw == null ? "optIn" : String.valueOf(raw);
    }

    private static String escapeXml(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    static final class HttpExchange {
        private final int status;
        private final String body;

        HttpExchange(int status, String body) {
            this.status = status;
            this.body = body;
        }

        int status() {
            return status;
        }

        String body() {
            return body;
        }

        static HttpExchange parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new HttpExchange(0, "");
            }
            int split = raw.indexOf("\r\n\r\n");
            if (split < 0) {
                split = raw.indexOf("\n\n");
            }
            String head = split < 0 ? raw : raw.substring(0, split);
            String body = split < 0 ? "" : raw.substring(split).replaceFirst("^\r?\n\r?\n", "");
            int status = 0;
            String first = head.lines().findFirst().orElse("");
            int firstSpace = first.indexOf(' ');
            int secondSpace = firstSpace < 0 ? -1 : first.indexOf(' ', firstSpace + 1);
            if (firstSpace >= 0 && secondSpace > firstSpace) {
                try {
                    status = Integer.parseInt(first.substring(firstSpace + 1, secondSpace));
                } catch (NumberFormatException ignored) {
                    status = 0;
                }
            }
            return new HttpExchange(status, body.trim());
        }
    }
}
