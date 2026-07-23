package com.ispf.driver.vmware;

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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VMware vSphere driver — vSphere SOAP API (vim25) client over HTTP(S).
 *
 * <p>{@link #connect()} performs {@code RetrieveServiceContent} against
 * {@code /sdk} to capture the ServiceInstance {@code about} info, then logs in
 * via {@code SessionManager.Login} when credentials are configured and keeps
 * the {@code vmware_soap_session} cookie for subsequent calls. Each poll
 * issues a {@code PropertyCollector.RetrieveProperties} request whose
 * {@code PropertySpec} path set is built from the mapped property paths
 * ({@code about.X} mappings expand to {@code content.about.X}); returned
 * {@code propSet} entries are exposed as ISPF variables. A
 * {@code NotAuthenticated} fault triggers one re-login and retry.
 * {@code SessionManager.Logout} is called on {@link #disconnect()}.
 *
 * <p>Limitations: hand-rolled XML (no vim25 WSDL codegen); the object set is
 * the ServiceInstance, so paths must be reachable from it; complex/array
 * values are flattened to their text content.
 */
public class VmwareDeviceDriver implements DeviceDriver {

    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("vmwareValue")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "vmware",
            "VMware vSphere Driver",
            "0.2.0",
            "vSphere SOAP client: RetrieveServiceContent + SessionManager Login +"
                    + " PropertyCollector RetrieveProperties; maps property paths"
                    + " (e.g. about.version) to ISPF variables",
            "ISPF",
            Map.of(
                    "host", "vcenter.local",
                    "username", "administrator@vsphere.local",
                    "password", "",
                    "timeoutMs", "10000",
                    "pollIntervalMs", "60000",
                    "useHttp", "false"
            )
    );

    private DriverObject driverObject;
    private String host = "vcenter.local";
    private String username = "administrator@vsphere.local";
    private String password = "";
    private long timeoutMs = 10000;
    private boolean useHttp;
    private HttpClient client;
    private String sessionCookie;
    private int lastStatusCode = -1;
    private volatile boolean lastConnectOk;
    private final Map<String, String> lastProperties = new ConcurrentHashMap<>();
    private final Map<String, VmwarePoint> points = new ConcurrentHashMap<>();
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
        if (value == null) {
            return;
        }
        switch (key) {
            case "host" -> host = value.trim();
            case "username" -> username = value;
            case "password" -> password = value;
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
        retrieveServiceContent();
        if (username != null && !username.isBlank()) {
            login();
        }
        connected = true;
        lastConnectOk = true;
        driverObject.log(DriverLogLevel.INFO, "VMware vSphere session established (host=" + host + ")");
    }

    @Override
    public void disconnect() {
        if (client != null && sessionCookie != null) {
            try {
                postSoap("""
                        <vim:Logout>
                          <vim:_this type="SessionManager">SessionManager</vim:_this>
                        </vim:Logout>
                        """);
            } catch (Exception e) {
                if (driverObject != null) {
                    driverObject.log(DriverLogLevel.DEBUG, "VMware Logout failed: " + e.getMessage());
                }
            }
        }
        connected = false;
        client = null;
        sessionCookie = null;
        lastProperties.clear();
        lastConnectOk = false;
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
        points.clear();
        Set<String> paths = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            VmwarePoint point = VmwarePoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            if (!point.isConnectedPoint()) {
                paths.add(normalizePath(point.propertyPath()));
            }
        }
        if (!paths.isEmpty()) {
            retrieveProperties(paths);
        }
        for (Map.Entry<String, VmwarePoint> entry : points.entrySet()) {
            driverObject.updateVariable(entry.getKey(), readPoint(entry.getValue()));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("VMware driver is read-only in v0.2");
    }

    /** {@code about.X} is shorthand for the ServiceInstance {@code content.about.X} path. */
    private static String normalizePath(String path) {
        if (path != null && path.startsWith("about.")) {
            return "content." + path;
        }
        return path;
    }

    private void retrieveServiceContent() throws DriverException {
        Document response = postSoapChecked("""
                <vim:RetrieveServiceContent>
                  <vim:_this type="ServiceInstance">ServiceInstance</vim:_this>
                </vim:RetrieveServiceContent>
                """);
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            Node returnval = (Node) xpath.evaluate(
                    "//*[local-name()='RetrieveServiceContentResponse']/*[local-name()='returnval']",
                    response, XPathConstants.NODE);
            if (returnval instanceof Element content) {
                collectAboutProperties(content);
            }
        } catch (Exception e) {
            throw new DriverException("Failed to parse RetrieveServiceContent response", e);
        }
    }

    private void collectAboutProperties(Element serviceContent) {
        NodeList children = serviceContent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element about && "about".equals(about.getLocalName())) {
                NodeList aboutChildren = about.getChildNodes();
                for (int j = 0; j < aboutChildren.getLength(); j++) {
                    if (aboutChildren.item(j) instanceof Element field) {
                        lastProperties.put("about." + field.getLocalName(), field.getTextContent().trim());
                    }
                }
            }
        }
    }

    private void login() throws DriverException {
        HttpResponse<String> response = postSoap("""
                <vim:Login>
                  <vim:_this type="SessionManager">SessionManager</vim:_this>
                  <vim:userName>%s</vim:userName>
                  <vim:password>%s</vim:password>
                </vim:Login>
                """.formatted(xmlEscape(username), xmlEscape(password)));
        Document document = parseResponse(response);
        String fault = faultText(document);
        if (fault != null) {
            throw new DriverException("VMware Login failed: " + fault);
        }
        sessionCookie = response.headers().firstValue("Set-Cookie")
                .filter(cookie -> cookie.contains("vmware_soap_session"))
                .map(cookie -> cookie.split(";", 2)[0])
                .orElse(null);
        if (sessionCookie == null) {
            throw new DriverException("VMware Login succeeded but returned no vmware_soap_session cookie");
        }
    }

    private void retrieveProperties(Set<String> paths) throws DriverException {
        try {
            retrievePropertiesOnce(paths);
        } catch (NotAuthenticatedException e) {
            driverObject.log(DriverLogLevel.DEBUG, "vSphere session expired, re-login");
            sessionCookie = null;
            login();
            retrievePropertiesOnce(paths);
        }
    }

    private void retrievePropertiesOnce(Set<String> paths) throws DriverException {
        StringBuilder pathSet = new StringBuilder();
        for (String path : paths) {
            pathSet.append("      <vim:pathSet>").append(xmlEscape(path)).append("</vim:pathSet>\n");
        }
        Document response;
        try {
            response = postSoapChecked("""
                    <vim:RetrieveProperties>
                      <vim:_this type="PropertyCollector">propertyCollector</vim:_this>
                      <vim:specSet>
                        <vim:propSet>
                          <vim:type>ServiceInstance</vim:type>
                    %s    </vim:propSet>
                        <vim:objectSet>
                          <vim:obj type="ServiceInstance">ServiceInstance</vim:obj>
                        </vim:objectSet>
                      </vim:specSet>
                    </vim:RetrieveProperties>
                    """.formatted(pathSet.toString()));
        } catch (DriverException e) {
            if (e.getMessage() != null && e.getMessage().contains("NotAuthenticated")) {
                throw new NotAuthenticatedException(e);
            }
            throw e;
        }
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList propSets = (NodeList) xpath.evaluate("//*[local-name()='propSet']",
                    response, XPathConstants.NODESET);
            for (int i = 0; i < propSets.getLength(); i++) {
                Node propSet = propSets.item(i);
                String name = xpath.evaluate("*[local-name()='name']", propSet);
                String value = xpath.evaluate("*[local-name()='val']", propSet);
                if (name != null && !name.isBlank()) {
                    lastProperties.put(name.trim(), value == null ? "" : value.trim());
                }
            }
        } catch (Exception e) {
            throw new DriverException("Failed to parse RetrieveProperties response", e);
        }
    }

    private Document postSoapChecked(String body) throws DriverException {
        Document document = parseResponse(postSoap(body));
        String fault = faultText(document);
        if (fault != null) {
            throw new DriverException("VMware SOAP fault: " + fault);
        }
        return document;
    }

    private HttpResponse<String> postSoap(String body) throws DriverException {
        try {
            String scheme = useHttp ? "http" : "https";
            String endpoint = scheme + "://" + host + "/sdk";
            String envelope = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <soapenv:Envelope xmlns:soapenv="%s"
                                      xmlns:vim="urn:vim25">
                      <soapenv:Body>
                        %s
                      </soapenv:Body>
                    </soapenv:Envelope>
                    """.formatted(SOAP_NS, body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope));
            if (sessionCookie != null) {
                builder.header("Cookie", sessionCookie);
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            return response;
        } catch (Exception e) {
            lastConnectOk = false;
            throw new DriverException("VMware SOAP request failed", e);
        }
    }

    private static Document parseResponse(HttpResponse<String> response) throws DriverException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            return documentBuilder.parse(new InputSource(new StringReader(
                    response.body() == null ? "" : response.body())));
        } catch (Exception e) {
            throw new DriverException("Failed to parse VMware SOAP response (HTTP "
                    + response.statusCode() + ")", e);
        }
    }

    /** Returns the SOAP Fault text, or {@code null} when the body carries no fault. */
    private static String faultText(Document document) {
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            Node fault = (Node) xpath.evaluate("//*[local-name()='Fault']", document, XPathConstants.NODE);
            if (fault == null) {
                return null;
            }
            String faultstring = xpath.evaluate("*[local-name()='faultstring']", fault);
            String detail = xpath.evaluate("string(*[local-name()='detail'])", fault);
            String detailType = xpath.evaluate("//*[local-name()='detail']/*/@*[local-name()='type']", document);
            String text = faultstring == null || faultstring.isBlank() ? "SOAP Fault" : faultstring.trim();
            if (detailType != null && !detailType.isBlank() && !text.contains(detailType.trim())) {
                text = text + " [" + detailType.trim() + "]";
            }
            return detail == null || detail.isBlank() ? text : text + " (" + detail.trim() + ")";
        } catch (Exception e) {
            return "unparseable SOAP Fault";
        }
    }

    private DataRecord readPoint(VmwarePoint point) {
        if (point.isConnectedPoint()) {
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", String.valueOf(lastConnectOk),
                    "statusCode", lastStatusCode
            ));
        }
        String value = resolveProperty(point.propertyPath());
        return DataRecord.single(VALUE_SCHEMA, Map.of("value", value, "statusCode", lastStatusCode));
    }

    private String resolveProperty(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String direct = lastProperties.get(path);
        if (direct != null) {
            return direct;
        }
        String normalized = lastProperties.get(normalizePath(path));
        if (normalized != null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : lastProperties.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(path) || entry.getKey().endsWith(path)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static String xmlEscape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Marker for an expired vSphere session; retried once after re-login. */
    private static final class NotAuthenticatedException extends DriverException {
        NotAuthenticatedException(DriverException cause) {
            super(cause.getMessage(), cause);
        }
    }
}
