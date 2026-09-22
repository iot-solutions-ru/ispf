package com.ispf.driver.mtconnect;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MTConnect agent client — HTTP/1.1 GET of Agent {@code /current} (or configured path) XML streams,
 * with a minimal JDK DOM parser for Samples / Events / Condition data items.
 * <p>
 * Point mapping is a data item id or name (for example {@code x_pos}, {@code Xact}, or
 * {@code name:Xact} / {@code id:x_pos}). Poll-only: agents expose read streams;
 * {@link #writePoint} is unsupported. Clean-room ISPF code, Apache-2.0 — JDK sockets + secure XML.
 */
public class MtconnectDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("mtconnectValue")
            .field("value", FieldType.STRING)
            .field("dataItemId", FieldType.STRING)
            .field("name", FieldType.STRING)
            .field("timestamp", FieldType.STRING)
            .field("sequence", FieldType.STRING)
            .field("category", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "mtconnect",
            "MTConnect Driver",
            "0.1.0",
            "HTTP/1.1 GET of MTConnect Agent /current or /sample XML; extracts data-item values",
            "ISPF",
            Map.of(
                    "baseUrl", "http://127.0.0.1:5000",
                    "path", "/current",
                    "timeoutMs", "3000",
                    "pollIntervalMs", "5000",
                    "device", ""
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String baseUrl = "http://127.0.0.1:5000";
    private String path = "/current";
    private long timeoutMs = 3000;
    private String deviceFilter = "";
    private final Map<String, MtconnectPoint> points = new ConcurrentHashMap<>();
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
            case "baseUrl" -> baseUrl = trimTrailingSlash(value.trim());
            case "path" -> path = normalizePath(value.trim());
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "device" -> deviceFilter = value.trim();
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "MTConnect ready (baseUrl=" + baseUrl + ", path=" + path + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
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
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String mapping = entry.getValue() == null || entry.getValue().isBlank()
                    ? entry.getKey()
                    : entry.getValue().trim();
            points.put(entry.getKey(), MtconnectPoint.parse(mapping));
        }
        Document document = fetchStreams();
        Map<String, DataItemSample> samples = indexDataItems(document);
        for (Map.Entry<String, MtconnectPoint> entry : points.entrySet()) {
            DataItemSample sample = resolve(samples, entry.getValue());
            if (sample == null) {
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", "",
                        "dataItemId", "",
                        "name", entry.getValue().selector(),
                        "timestamp", "",
                        "sequence", "",
                        "category", ""
                )));
            } else {
                driverObject.updateVariable(entry.getKey(), DataRecord.single(VALUE_SCHEMA, Map.of(
                        "value", sample.value(),
                        "dataItemId", sample.dataItemId(),
                        "name", sample.name(),
                        "timestamp", sample.timestamp(),
                        "sequence", sample.sequence(),
                        "category", sample.category()
                )));
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("MTConnect driver is poll-only; writePoint is not supported");
    }

    /**
     * Builds an HTTP/1.1 GET request line and headers for the Agent path.
     * The request always starts with {@code GET } and contains {@code HTTP/1.1}.
     */
    static String buildGetRequest(String path, String hostHeader) {
        String requestPath = path == null || path.isBlank() ? "/current" : normalizePath(path);
        return "GET " + requestPath + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "Accept: application/xml, text/xml, */*\r\n"
                + "Connection: close\r\n"
                + "\r\n";
    }

    private Document fetchStreams() throws DriverException {
        URI uri = URI.create(baseUrl + path);
        String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        String requestPath = uri.getRawPath() == null || uri.getRawPath().isBlank() ? path : uri.getRawPath();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            requestPath = requestPath + "?" + uri.getRawQuery();
        }
        String hostHeader = host + ":" + port;
        String request = buildGetRequest(requestPath, hostHeader);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) timeoutMs);
            socket.setSoTimeout((int) timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            HttpResponse response = readHttpResponse(in);
            if (response.statusCode < 200 || response.statusCode >= 300) {
                throw new DriverException("MTConnect GET failed for " + baseUrl + path
                        + ": HTTP " + response.statusCode);
            }
            return parseXml(response.body);
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("MTConnect GET failed for " + baseUrl + path, e);
        }
    }

    static HttpResponse readHttpResponse(InputStream in) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null || statusLine.isBlank()) {
            throw new IOException("EOF reading HTTP status line");
        }
        int statusCode = 0;
        String[] parts = statusLine.split("\\s+", 3);
        if (parts.length >= 2) {
            try {
                statusCode = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        Map<String, String> headers = new LinkedHashMap<>();
        while (true) {
            String line = readLine(in);
            if (line == null) {
                throw new IOException("EOF reading HTTP headers");
            }
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }
        int contentLength = -1;
        String cl = headers.get("content-length");
        if (cl != null && !cl.isBlank()) {
            contentLength = Integer.parseInt(cl.trim());
        }
        String body;
        if (contentLength >= 0) {
            body = new String(in.readNBytes(contentLength), StandardCharsets.UTF_8);
        } else {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new HttpResponse(statusCode, body);
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch < 0) {
                if (line.size() == 0) {
                    return null;
                }
                break;
            }
            if (ch == '\n') {
                break;
            }
            if (ch != '\r') {
                line.write(ch);
            }
        }
        return line.toString(StandardCharsets.US_ASCII);
    }

    static Document parseXml(String xml) throws DriverException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new DriverException("Failed to parse MTConnect XML", e);
        }
    }

    private Map<String, DataItemSample> indexDataItems(Document document) {
        Map<String, DataItemSample> byKey = new LinkedHashMap<>();
        walk(document.getDocumentElement(), byKey);
        return byKey;
    }

    private void walk(Node node, Map<String, DataItemSample> byKey) {
        if (node == null) {
            return;
        }
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String local = localName(element);
            if ("DeviceStream".equals(local) && !deviceFilter.isBlank()) {
                String name = attr(element, "name");
                String uuid = attr(element, "uuid");
                if (!deviceFilter.equals(name) && !deviceFilter.equals(uuid)) {
                    return;
                }
            }
            if (isDataItemElement(local, element)) {
                DataItemSample sample = DataItemSample.from(element, categoryFor(local, element));
                if (!sample.dataItemId().isBlank()) {
                    byKey.putIfAbsent("id:" + sample.dataItemId().toLowerCase(Locale.ROOT), sample);
                    byKey.putIfAbsent(sample.dataItemId().toLowerCase(Locale.ROOT), sample);
                }
                if (!sample.name().isBlank()) {
                    byKey.putIfAbsent("name:" + sample.name().toLowerCase(Locale.ROOT), sample);
                    byKey.putIfAbsent(sample.name().toLowerCase(Locale.ROOT), sample);
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            walk(children.item(i), byKey);
        }
    }

    private static boolean isDataItemElement(String local, Element element) {
        if (local == null || local.isBlank()) {
            return false;
        }
        if ("Header".equals(local) || local.endsWith("Streams") || local.endsWith("Stream")
                || "Samples".equals(local) || "Events".equals(local) || "Condition".equals(local)
                || "MTConnectStreams".equals(local) || "MTConnectDevices".equals(local)
                || "MTConnectError".equals(local) || "Asset".equals(local) || "Assets".equals(local)) {
            return false;
        }
        return element.hasAttribute("dataItemId") || element.hasAttribute("name");
    }

    private static String categoryFor(String local, Element element) {
        String category = attr(element, "category");
        if (!category.isBlank()) {
            return category;
        }
        Node parent = element.getParentNode();
        if (parent instanceof Element parentElement) {
            String parentLocal = localName(parentElement);
            if ("Samples".equals(parentLocal) || "Events".equals(parentLocal) || "Condition".equals(parentLocal)) {
                return parentLocal;
            }
        }
        return local;
    }

    private DataItemSample resolve(Map<String, DataItemSample> samples, MtconnectPoint point) {
        String key = point.key();
        DataItemSample direct = samples.get(key);
        if (direct != null) {
            return direct;
        }
        return samples.get(point.selector().toLowerCase(Locale.ROOT));
    }

    static String localName(Element element) {
        String local = element.getLocalName();
        if (local != null && !local.isBlank()) {
            return local;
        }
        String tagged = element.getTagName();
        int colon = tagged.indexOf(':');
        return colon >= 0 ? tagged.substring(colon + 1) : tagged;
    }

    static String attr(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null ? "" : value.trim();
    }

    static String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    static String normalizePath(String raw) {
        if (raw.startsWith("/")) {
            return raw;
        }
        return "/" + raw;
    }

    static final class HttpResponse {
        final int statusCode;
        final String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }

    record DataItemSample(
            String value,
            String dataItemId,
            String name,
            String timestamp,
            String sequence,
            String category
    ) {
        static DataItemSample from(Element element, String category) {
            String text = element.getTextContent() == null ? "" : element.getTextContent().trim();
            return new DataItemSample(
                    text,
                    attr(element, "dataItemId"),
                    attr(element, "name"),
                    attr(element, "timestamp"),
                    attr(element, "sequence"),
                    category == null ? "" : category
            );
        }
    }
}
