package com.ispf.driver.cwmp;

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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TR-069/CWMP ACS client stub — POSTs Inform SOAP envelopes to an ACS URL.
 */
public class CwmpDeviceDriver implements DeviceDriver {

    private static final Pattern PARAM_PATTERN = Pattern.compile(
            "<Name>([^<]+)</Name>\\s*<Value[^>]*>([^<]*)</Value>",
            Pattern.CASE_INSENSITIVE
    );

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("cwmpValue")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "cwmp",
            "TR-069 CWMP Client Driver",
            "0.2.0",
            "TR-069 CPE client: Periodic Inform to ACS, parses ACS RPC responses and GetParameterValues results",
            "ISPF",
            Map.of(
                    "acsUrl", "http://127.0.0.1:7547/",
                    "deviceId", "000000-000000000000",
                    "periodicInformInterval", "300",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "300000",
                    "informParameters", "Device.DeviceInfo.SoftwareVersion,Device.DeviceInfo.HardwareVersion"
            )
    );

    private DriverObject driverObject;
    private String acsUrl = "http://127.0.0.1:7547/";
    private String deviceId = "000000-000000000000";
    private long periodicInformInterval = 300;
    private long timeoutMs = 5000;
    private java.util.List<String> informParameters = java.util.List.of("Device.DeviceInfo.SoftwareVersion");
    private HttpClient client;
    private int lastStatusCode = -1;
    private volatile boolean lastInformOk;
    private final Map<String, String> lastParameters = new ConcurrentHashMap<>();
    private final Map<String, CwmpPoint> points = new ConcurrentHashMap<>();
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
            case "acsUrl" -> acsUrl = value.trim();
            case "deviceId" -> deviceId = value.trim();
            case "periodicInformInterval" -> periodicInformInterval = Long.parseLong(value.trim());
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            case "informParameters" -> informParameters = parseInformParameters(value.trim());
            default -> { }
        }
    }

    static java.util.List<String> parseInformParameters(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.List.of("Device.DeviceInfo.SoftwareVersion");
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO,
                "CWMP client ready (acsUrl=" + acsUrl + ", deviceId=" + deviceId + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        lastParameters.clear();
        lastInformOk = false;
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
        sendInform();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            CwmpPoint point = CwmpPoint.parse(entry.getValue());
            points.put(entry.getKey(), point);
            driverObject.updateVariable(entry.getKey(), readPoint(point));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("CWMP driver is read-only in v0.1");
    }

    private void sendInform() throws DriverException {
        try {
            String envelope = buildInformEnvelope();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(acsUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            lastInformOk = lastStatusCode >= 200 && lastStatusCode < 300;
            lastParameters.clear();
            if (response.body() != null) {
                parseParameters(response.body(), lastParameters);
                handleAcsRpc(response.body());
            }
        } catch (Exception e) {
            lastInformOk = false;
            throw new DriverException("CWMP Inform failed", e);
        }
    }

    private DataRecord readPoint(CwmpPoint point) {
        if (point.isConnectedPoint()) {
            return DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", String.valueOf(lastInformOk),
                    "statusCode", lastStatusCode
            ));
        }
        String value = lastParameters.getOrDefault(point.parameterName(), "");
        if (value.isEmpty()) {
            for (Map.Entry<String, String> entry : lastParameters.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(point.parameterName())) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return DataRecord.single(VALUE_SCHEMA, Map.of("value", value, "statusCode", lastStatusCode));
    }

    static void parseParameters(String xml, Map<String, String> target) {
        Matcher matcher = PARAM_PATTERN.matcher(xml);
        while (matcher.find()) {
            target.put(matcher.group(1).trim(), matcher.group(2).trim());
        }
    }

    private void handleAcsRpc(String responseBody) throws DriverException {
        if (!responseBody.contains("GetParameterValues")) {
            return;
        }
        java.util.List<String> requested = extractGetParameterNames(responseBody);
        if (requested.isEmpty()) {
            return;
        }
        String response = postSoap(buildGetParameterValuesResponse(requested));
        parseParameters(response, lastParameters);
    }

    static java.util.List<String> extractGetParameterNames(String xml) {
        Pattern pattern = Pattern.compile(
                "<string>([^<]+)</string>",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(xml);
        java.util.List<String> names = new java.util.ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (name.startsWith("Device.") || name.startsWith("InternetGatewayDevice.")) {
                names.add(name);
            }
        }
        return names;
    }

    private String postSoap(String envelope) throws DriverException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(acsUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            return response.body() == null ? "" : response.body();
        } catch (Exception e) {
            throw new DriverException("CWMP SOAP exchange failed", e);
        }
    }

    private String buildGetParameterValuesResponse(java.util.List<String> parameterNames) {
        StringBuilder values = new StringBuilder();
        for (String name : parameterNames) {
            String value = lastParameters.getOrDefault(name, "ISPF-CWMP-PLACEHOLDER");
            values.append("""
                      <ParameterValueStruct>
                        <Name>%s</Name>
                        <Value>%s</Value>
                      </ParameterValueStruct>
                    """.formatted(name, escapeXml(value)));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/"
                                   xmlns:cwmp="urn:dslforum-org:cwmp-1-0">
                  <soap-env:Header>
                    <cwmp:ID soap-env:mustUnderstand="1">2</cwmp:ID>
                  </soap-env:Header>
                  <soap-env:Body>
                    <cwmp:GetParameterValuesResponse>
                      <ParameterList soap-env:arrayType="cwmp:ParameterValueStruct[%d]">
                %s
                      </ParameterList>
                    </cwmp:GetParameterValuesResponse>
                  </soap-env:Body>
                </soap-env:Envelope>
                """.formatted(parameterNames.size(), values);
    }

    private static String escapeXml(String raw) {
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String buildInformEnvelope() {
        StringBuilder parameterValues = new StringBuilder();
        for (String name : informParameters) {
            parameterValues.append("""
                        <ParameterValueStruct>
                          <Name>%s</Name>
                          <Value>ISPF</Value>
                        </ParameterValueStruct>
                    """.formatted(name));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/"
                                   xmlns:cwmp="urn:dslforum-org:cwmp-1-0">
                  <soap-env:Header>
                    <cwmp:ID soap-env:mustUnderstand="1">1</cwmp:ID>
                  </soap-env:Header>
                  <soap-env:Body>
                    <cwmp:Inform>
                      <DeviceId>
                        <SerialNumber>%s</SerialNumber>
                      </DeviceId>
                      <Event soap-env:arrayType="cwmp:EventStruct[1]">
                        <EventStruct>
                          <EventCode>2 PERIODIC</EventCode>
                          <CommandKey></CommandKey>
                        </EventStruct>
                      </Event>
                      <MaxEnvelopes>1</MaxEnvelopes>
                      <CurrentTime>2020-01-01T00:00:00Z</CurrentTime>
                      <RetryCount>0</RetryCount>
                      <ParameterList soap-env:arrayType="cwmp:ParameterValueStruct[%d]">
                %s
                      </ParameterList>
                    </cwmp:Inform>
                  </soap-env:Body>
                </soap-env:Envelope>
                """.formatted(deviceId, informParameters.size(), parameterValues);
    }
}
