package com.ispf.driver.smis;

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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SMI-S driver — CIM-XML over HTTP(S) client that enumerates
 * {@code CIM_RegisteredProfile} instances from the configured provider and
 * exposes their properties as ISPF variables.
 *
 * <p>Each poll sends an {@code EnumerateInstances} intrinsic method call and
 * parses the {@code SIMPLERSP} response: {@code INSTANCE} elements are
 * flattened to {@code ClassName:PropertyName} keys holding the {@code VALUE}
 * text ({@code PROPERTY.ARRAY} entries are joined with commas). A CIM
 * {@code ERROR} response or a non-success HTTP status fails the read with a
 * {@link DriverException}; properties absent from the response read back as
 * {@code NOT_AVAILABLE}.
 */
public class SmisDeviceDriver implements DeviceDriver {

    private static final String NOT_AVAILABLE = "NOT_AVAILABLE";

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("smisValue")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "smi-s",
            "SMI-S CIM Driver",
            "0.1.0",
            "Enumerates CIM_RegisteredProfile instances from an SMI-S provider over CIM-XML (HTTP/HTTPS)"
                    + " and maps their properties to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "127.0.0.1",
                    "port", "5989",
                    "username", "admin",
                    "password", "",
                    "namespace", "root/pg",
                    "timeoutMs", "10000",
                    "pollIntervalMs", "60000",
                    "useHttp", "false"
            )
    );

    private DriverObject driverObject;
    private String host = "127.0.0.1";
    private int port = 5989;
    private String username = "admin";
    private String password = "";
    private String namespace = "root/pg";
    private long timeoutMs = 10000;
    private boolean useHttp;
    private HttpClient client;
    private int lastStatusCode = -1;
    private final Map<String, String> properties = new ConcurrentHashMap<>();
    private final Map<String, SmisPoint> points = new ConcurrentHashMap<>();
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
            case "username" -> username = value;
            case "password" -> password = value;
            case "namespace" -> namespace = value.trim();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "useHttp" -> useHttp = Boolean.parseBoolean(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "SMI-S client ready (host=" + host + ", namespace=" + namespace + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        properties.clear();
        lastStatusCode = -1;
    }

    @Override
    public boolean isConnected() {
        return connected && client != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        enumerateProfiles();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            SmisPoint point = SmisPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readProperty(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("SMI-S driver is read-only in v0.1");
    }

    private void enumerateProfiles() throws DriverException {
        try {
            String scheme = useHttp ? "http" : "https";
            String endpoint = scheme + "://" + host + ":" + port + "/cimom";
            String envelope = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <CIM CIMVERSION="2.0" DTDVERSION="2.0">
                      <MESSAGE ID="1" PROTOCOLVERSION="1.0">
                        <SIMPLEREQ>
                          <IMETHODCALL NAME="EnumerateInstances">
                            <LOCALNAMESPACEPATH>
                              %s
                            </LOCALNAMESPACEPATH>
                            <IPARAMVALUE NAME="ClassName">
                              <CLASSNAME NAME="CIM_RegisteredProfile"/>
                            </IPARAMVALUE>
                          </IMETHODCALL>
                        </SIMPLEREQ>
                      </MESSAGE>
                    </CIM>
                    """.formatted(namespacePathElements(namespace));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope));
            if (username != null && !username.isBlank()) {
                String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
                builder.header("Authorization", "Basic " + token);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            if (lastStatusCode < 200 || lastStatusCode >= 300) {
                properties.clear();
                throw new DriverException("SMI-S provider returned HTTP " + lastStatusCode);
            }
            parseInstances(response.body());
            driverObject.log(DriverLogLevel.DEBUG,
                    "SMI-S enumeration parsed " + properties.size() + " properties");
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            properties.clear();
            throw new DriverException("SMI-S profile enumeration failed", e);
        }
    }

    /**
     * Parses a CIM-XML {@code SIMPLERSP} body into the {@link #properties} map.
     * A CIM {@code ERROR} element aborts the read with its code and description.
     */
    private void parseInstances(String body) throws DriverException {
        properties.clear();
        if (body == null || body.isBlank()) {
            return;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            Document document = documentBuilder.parse(new InputSource(new StringReader(body)));

            XPath xpath = XPathFactory.newInstance().newXPath();
            Node error = (Node) xpath.evaluate(
                    "/CIM/MESSAGE/SIMPLERSP/IMETHODRESPONSE/ERROR", document, XPathConstants.NODE);
            if (error instanceof Element errorElement) {
                String code = errorElement.getAttribute("CODE");
                String description = errorElement.getAttribute("DESCRIPTION");
                throw new DriverException("SMI-S CIM error " + code
                        + (description.isBlank() ? "" : ": " + description));
            }

            NodeList instances = (NodeList) xpath.evaluate(
                    "/CIM/MESSAGE/SIMPLERSP/IMETHODRESPONSE/IRETURNVALUE//INSTANCE",
                    document, XPathConstants.NODESET);
            for (int i = 0; i < instances.getLength(); i++) {
                collectInstanceProperties((Element) instances.item(i));
            }
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("Failed to parse SMI-S CIM-XML response", e);
        }
    }

    private void collectInstanceProperties(Element instance) {
        String className = instance.getAttribute("CLASSNAME");
        if (className.isBlank()) {
            return;
        }
        NodeList children = instance.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element property)) {
                continue;
            }
            String name = property.getAttribute("NAME");
            if (name.isBlank()) {
                continue;
            }
            String value = switch (property.getTagName()) {
                case "PROPERTY" -> propertyValue(property);
                case "PROPERTY.ARRAY" -> propertyArrayValue(property);
                default -> null;
            };
            // A property without VALUE is CIM null; skip it so it reads back as NOT_AVAILABLE.
            // First occurrence wins: several instances of a class share the same key.
            if (value != null) {
                properties.putIfAbsent(className + ":" + name, value);
            }
        }
    }

    private String propertyValue(Element property) {
        NodeList values = property.getElementsByTagName("VALUE");
        if (values.getLength() == 0) {
            return null;
        }
        return values.item(0).getTextContent().trim();
    }

    private String propertyArrayValue(Element property) {
        NodeList values = property.getElementsByTagName("VALUE");
        List<String> items = new ArrayList<>();
        for (int i = 0; i < values.getLength(); i++) {
            items.add(values.item(i).getTextContent().trim());
        }
        return String.join(",", items);
    }

    /** One NAMESPACE element per path segment, e.g. {@code root/pg} → root + pg. */
    private static String namespacePathElements(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .map(segment -> "<NAMESPACE NAME=\"" + xmlEscape(segment.trim()) + "\"/>")
                .collect(Collectors.joining("\n"));
    }

    private static String xmlEscape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    private DataRecord readProperty(SmisPoint point) {
        String key = point.className() + ":" + point.propertyName();
        String value = properties.getOrDefault(key, "");
        if (value.isEmpty()) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        if (value.isEmpty()) {
            value = NOT_AVAILABLE;
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of("value", value, "statusCode", lastStatusCode));
    }
}
