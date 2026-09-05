package com.ispf.driver.ieee20305;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IEEE 2030.5 (SEP2) HTTP/XML client — GET subset for EndDeviceList and MeterReading-style
 * resources. Not a full SEP2 function set (no mTLS enrollment, subscription, or DER control).
 * <p>
 * Point mapping is an absolute resource path with optional {@code :field}
 * ({@code /edev}, {@code /edev:sFDI}, {@code /upt/1/mr/1/r:value}). Clean-room ISPF code,
 * Apache-2.0 — JDK {@link HttpClient} only, no proprietary SEP2 stacks.
 */
public class Ieee20305DeviceDriver implements DeviceDriver {

    private static final Pattern ELEMENT = Pattern.compile(
            "<([A-Za-z_][\\w.-]*)(?:\\s[^>]*)?>([^<]*)</\\1>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ATTR_HREF = Pattern.compile(
            "\\bhref\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("ieee20305Value")
            .field("value", FieldType.STRING)
            .field("path", FieldType.STRING)
            .field("field", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "ieee2030-5",
            "IEEE 2030.5 SEP2 Driver",
            "0.1.0",
            "IEEE 2030.5 (SEP2) HTTP/XML GET subset for EndDeviceList and MeterReading-style resources",
            "ISPF",
            Map.of(
                    "baseUrl", "http://127.0.0.1:8080",
                    "timeoutMs", "5000",
                    "accept", "application/sep+xml"
            ),
            null,
            Set.of("read")
    );

    private DriverObject driverObject;
    private String baseUrl = "http://127.0.0.1:8080";
    private long timeoutMs = 5000;
    private String accept = "application/sep+xml";
    private HttpClient client;
    private final Map<String, Ieee20305Point> points = new ConcurrentHashMap<>();
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
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "accept" -> accept = value.trim();
            default -> { }
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "IEEE 2030.5 SEP2 client ready (baseUrl=" + baseUrl + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        points.clear();
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
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            Ieee20305Point point = Ieee20305Point.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), getResource(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("IEEE 2030.5 SEP2 driver is GET-only in v0.1");
    }

    private DataRecord getResource(Ieee20305Point point) throws DriverException {
        try {
            URI uri = URI.create(baseUrl + point.path());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", accept)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new DriverException("IEEE 2030.5 GET " + point.path() + " returned HTTP " + status);
            }
            String body = response.body() == null ? "" : response.body();
            String extracted = extractField(body, point.field());
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", extracted,
                    "path", point.path(),
                    "field", point.field(),
                    "statusCode", status
            ));
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("IEEE 2030.5 GET failed for " + point.path(), e);
        }
    }

    static String extractField(String xml, String field) throws DriverException {
        if (xml == null || xml.isBlank()) {
            throw new DriverException("Empty IEEE 2030.5 XML body");
        }
        if ("href".equalsIgnoreCase(field)) {
            Matcher href = ATTR_HREF.matcher(xml);
            if (href.find()) {
                return href.group(1).trim();
            }
        }
        Matcher matcher = ELEMENT.matcher(xml);
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(field)) {
                return matcher.group(2).trim();
            }
        }
        // Case-insensitive local-name match ignoring XML namespace prefixes (ns:sFDI)
        Pattern prefixed = Pattern.compile(
                "<(?:[A-Za-z_][\\w.-]*:)?(" + Pattern.quote(field) + ")(?:\\s[^>]*)?>([^<]*)</(?:[A-Za-z_][\\w.-]*:)?\\1>",
                Pattern.CASE_INSENSITIVE
        );
        Matcher prefixedMatcher = prefixed.matcher(xml);
        if (prefixedMatcher.find()) {
            return prefixedMatcher.group(2).trim();
        }
        throw new DriverException("IEEE 2030.5 field '" + field + "' not found in response");
    }
}
