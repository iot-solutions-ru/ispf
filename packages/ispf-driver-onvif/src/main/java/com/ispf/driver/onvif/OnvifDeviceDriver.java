package com.ispf.driver.onvif;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ONVIF Device service client — clean-room SOAP/HTTP subset for lab and CI.
 * <p>
 * Supports {@code GetDeviceInformation}, {@code GetCapabilities}, {@code GetHostname},
 * and {@code SetHostname} against a device service URL. This is not a full ONVIF Profile S/T
 * stack and does not use a proprietary ONVIF SDK — JDK {@link HttpClient} plus XML text only.
 * Clean-room ISPF code, Apache-2.0.
 */
public class OnvifDeviceDriver implements DeviceDriver {

    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String TDS_NS = "http://www.onvif.org/ver10/device/wsdl";
    private static final String TT_NS = "http://www.onvif.org/ver10/schema";

    private static final Pattern TAG = Pattern.compile(
            "<(?:[A-Za-z0-9_]+:)?([A-Za-z0-9_]+)[^>]*>([^<]*)</(?:[A-Za-z0-9_]+:)?\\1>",
            Pattern.CASE_INSENSITIVE
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("onvifValue")
            .field("value", FieldType.STRING)
            .field("field", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "onvif",
            "ONVIF Device Driver",
            "0.1.0",
            "ONVIF Device SOAP subset: GetDeviceInformation, GetCapabilities, Get/SetHostname",
            "ISPF",
            Map.of(
                    "deviceServiceUrl", "http://127.0.0.1:80/onvif/device_service",
                    "timeoutMs", "3000"
            ),
            null,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private String deviceServiceUrl = "http://127.0.0.1:80/onvif/device_service";
    private int timeoutMs = 3000;
    private HttpClient client;
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private final Map<String, String> lastInfo = new ConcurrentHashMap<>();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    private void applyConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "deviceServiceUrl", "baseUrl", "host" -> {
                if ("host".equals(key) && !value.contains("://")) {
                    deviceServiceUrl = "http://" + value.trim() + ":" + portFromConfig() + "/onvif/device_service";
                } else if ("baseUrl".equals(key)) {
                    String base = value.trim().replaceAll("/$", "");
                    deviceServiceUrl = base.endsWith("device_service") ? base : base + "/onvif/device_service";
                } else {
                    deviceServiceUrl = value.trim();
                }
            }
            case "port" -> {
                // applied lazily via host rewrite when host is set after port
            }
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    private int portFromConfig() {
        String port = driverObject == null ? null : driverObject.configuration().get("port");
        if (port == null || port.isBlank()) {
            return 80;
        }
        return Integer.parseInt(port.trim());
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "ONVIF device service ready at " + deviceServiceUrl);
    }

    @Override
    public void disconnect() {
        connected = false;
        points.clear();
        lastInfo.clear();
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
        Map<String, String> info = fetchDeviceInformation();
        Map<String, String> caps = fetchCapabilities();
        String hostname = fetchHostname();
        lastInfo.clear();
        lastInfo.putAll(info);
        lastInfo.putAll(caps);
        lastInfo.put("Hostname", hostname);

        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String pointId = entry.getKey();
            String field = normalizeField(entry.getValue() == null || entry.getValue().isBlank()
                    ? pointId : entry.getValue());
            points.put(pointId, field);
            String value = lastInfo.getOrDefault(field, "");
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value,
                    "field", field
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        String field = points.get(pointId);
        if (field == null) {
            throw new DriverException("Unknown point: " + pointId);
        }
        if (!"Hostname".equals(field)) {
            throw new DriverException("ONVIF write supports Hostname only, got: " + field);
        }
        String hostname = extractValue(value);
        setHostname(hostname);
        lastInfo.put("Hostname", hostname);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", hostname,
                "field", field
        )));
    }

    private Map<String, String> fetchDeviceInformation() throws DriverException {
        String body = soap("tds:GetDeviceInformation", "");
        String response = post(body, "http://www.onvif.org/ver10/device/wsdl/GetDeviceInformation");
        Map<String, String> tags = extractTags(response);
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : List.of("Manufacturer", "Model", "FirmwareVersion", "SerialNumber", "HardwareId")) {
            out.put(key, tags.getOrDefault(key, ""));
        }
        return out;
    }

    private Map<String, String> fetchCapabilities() throws DriverException {
        String body = soap("tds:GetCapabilities", "<tds:Category>All</tds:Category>");
        String response = post(body, "http://www.onvif.org/ver10/device/wsdl/GetCapabilities");
        Map<String, String> tags = extractTags(response);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("DeviceXAddr", firstNonBlank(tags, "XAddr", "Device"));
        out.put("MediaXAddr", tags.getOrDefault("XAddr", ""));
        // Prefer Device capability XAddr when nested parsing finds multiple — scan explicitly
        Matcher deviceBlock = Pattern.compile(
                "<(?:[A-Za-z0-9_]+:)?Device\\b[\\s\\S]*?<(?:[A-Za-z0-9_]+:)?XAddr>([^<]*)</(?:[A-Za-z0-9_]+:)?XAddr>",
                Pattern.CASE_INSENSITIVE
        ).matcher(response);
        if (deviceBlock.find()) {
            out.put("DeviceXAddr", deviceBlock.group(1).trim());
        }
        Matcher mediaBlock = Pattern.compile(
                "<(?:[A-Za-z0-9_]+:)?Media\\b[\\s\\S]*?<(?:[A-Za-z0-9_]+:)?XAddr>([^<]*)</(?:[A-Za-z0-9_]+:)?XAddr>",
                Pattern.CASE_INSENSITIVE
        ).matcher(response);
        if (mediaBlock.find()) {
            out.put("MediaXAddr", mediaBlock.group(1).trim());
        }
        return out;
    }

    private String fetchHostname() throws DriverException {
        String body = soap("tds:GetHostname", "");
        String response = post(body, "http://www.onvif.org/ver10/device/wsdl/GetHostname");
        Map<String, String> tags = extractTags(response);
        return tags.getOrDefault("Name", tags.getOrDefault("Hostname", ""));
    }

    private void setHostname(String hostname) throws DriverException {
        String inner = "<tds:FromDHCP>false</tds:FromDHCP><tds:Name>" + xmlEscape(hostname) + "</tds:Name>";
        String body = soap("tds:SetHostname", inner);
        post(body, "http://www.onvif.org/ver10/device/wsdl/SetHostname");
    }

    private String soap(String operation, String innerXml) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <s:Envelope xmlns:s="%s" xmlns:tds="%s" xmlns:tt="%s">
                  <s:Body>
                    <%s>%s</%s>
                  </s:Body>
                </s:Envelope>
                """.formatted(SOAP_NS, TDS_NS, TT_NS, operation, innerXml, operation);
    }

    private String post(String soapBody, String action) throws DriverException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(deviceServiceUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/soap+xml; charset=utf-8")
                    .header("SOAPAction", "\"" + action + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(soapBody))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new DriverException("ONVIF HTTP " + response.statusCode() + " for " + action);
            }
            return response.body() == null ? "" : response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DriverException("ONVIF SOAP failed for " + deviceServiceUrl, e);
        }
    }

    static Map<String, String> extractTags(String xml) {
        Map<String, String> out = new LinkedHashMap<>();
        if (xml == null || xml.isBlank()) {
            return out;
        }
        Matcher matcher = TAG.matcher(xml);
        while (matcher.find()) {
            out.put(matcher.group(1), matcher.group(2).trim());
        }
        return out;
    }

    static String normalizeField(String raw) {
        String trimmed = raw.trim();
        return switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "manufacturer" -> "Manufacturer";
            case "model" -> "Model";
            case "firmwareversion", "firmware" -> "FirmwareVersion";
            case "serialnumber", "serial" -> "SerialNumber";
            case "hardwareid", "hardware" -> "HardwareId";
            case "devicexaddr", "device", "capabilities.device" -> "DeviceXAddr";
            case "mediaxaddr", "media" -> "MediaXAddr";
            case "hostname", "name" -> "Hostname";
            default -> trimmed;
        };
    }

    private static String firstNonBlank(Map<String, String> tags, String... keys) {
        for (String key : keys) {
            String value = tags.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String extractValue(DataRecord value) {
        if (value == null || value.rowCount() == 0) {
            return "";
        }
        Map<String, Object> row = value.firstRow();
        for (String key : List.of("value", "payload", "data", "text", "raw", "hostname")) {
            Object candidate = row.get(key);
            if (candidate != null) {
                return String.valueOf(candidate);
            }
        }
        if (row.size() == 1) {
            return String.valueOf(row.values().iterator().next());
        }
        return row.toString();
    }

    private static String xmlEscape(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
