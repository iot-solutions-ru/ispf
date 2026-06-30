package com.ispf.driver.haystack;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Polls an external Project Haystack server (SkySpark, FIN, Haxall) via HTTP JSON {@code read}.
 */
public class HaystackDeviceDriver implements DeviceDriver {

    private static final DataSchema POINT_SCHEMA = DataSchema.builder("haystackPointValue")
            .field("ref", FieldType.STRING)
            .field("value", FieldType.DOUBLE)
            .field("valueText", FieldType.STRING)
            .field("unit", FieldType.STRING)
            .field("dis", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "haystack",
            "Haystack Client Driver",
            "0.1.0",
            "Polls external Project Haystack servers via HTTP JSON read (SkySpark/FIN/Haxall)",
            "ISPF",
            Map.of(
                    "baseUrl", "http://127.0.0.1:8080",
                    "project", "demo",
                    "username", "",
                    "password", "",
                    "authToken", "",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000"
            ),
            DriverMaturity.BETA
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String baseUrl = "http://127.0.0.1:8080";
    private String project = "demo";
    private String username = "";
    private String password = "";
    private String authToken = "";
    private long timeoutMs = 5000;
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
            case "project" -> project = value.trim();
            case "username" -> username = value.trim();
            case "password" -> password = value;
            case "authToken" -> authToken = value.trim();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
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
        postGrid("/about", HaystackJsonGrid.buildAboutRequest());
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Haystack client ready (baseUrl=" + baseUrl + ", project=" + project + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        driverObject.log(DriverLogLevel.INFO, "Haystack client disconnected");
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
        if (pointMappings.isEmpty()) {
            return;
        }

        Map<String, String> variableByRef = new LinkedHashMap<>();
        List<String> refs = new ArrayList<>();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            HaystackPoint point = HaystackPoint.parse(entry.getValue());
            variableByRef.put(point.ref(), entry.getKey());
            refs.add(point.ref());
        }

        String responseBody = postGrid("/read", HaystackJsonGrid.buildReadRequest(refs));
        Map<String, HaystackJsonGrid.HaystackPointValue> values = HaystackJsonGrid.parseReadResponse(responseBody);
        for (Map.Entry<String, String> entry : variableByRef.entrySet()) {
            HaystackJsonGrid.HaystackPointValue value = values.get(entry.getKey());
            if (value == null) {
                driverObject.log(DriverLogLevel.WARNING, "Haystack read returned no row for ref " + entry.getKey());
                continue;
            }
            driverObject.updateVariable(entry.getValue(), toRecord(value));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Haystack driver is read-only in v0.1");
    }

    private String postGrid(String operation, String body) throws DriverException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl(operation)))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", HaystackJsonGrid.JSON_MEDIA_TYPE)
                    .header("Accept", HaystackJsonGrid.JSON_MEDIA_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            applyAuth(builder);

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("Haystack HTTP " + response.statusCode() + " for " + operation);
            }
            return response.body() == null ? "" : response.body();
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("Haystack request failed for " + operation, e);
        }
    }

    private void applyAuth(HttpRequest.Builder builder) {
        if (!authToken.isBlank()) {
            builder.header("Authorization", "Bearer " + authToken);
            return;
        }
        if (!username.isBlank()) {
            String token = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8)
            );
            builder.header("Authorization", "Basic " + token);
        }
    }

    private String apiUrl(String operation) {
        return baseUrl + "/api/" + project + operation;
    }

    private static DataRecord toRecord(HaystackJsonGrid.HaystackPointValue value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ref", value.ref());
        row.put("unit", value.unit());
        row.put("dis", value.dis());
        Object curVal = value.curVal();
        if (curVal instanceof Boolean boolVal) {
            row.put("value", boolVal ? 1.0 : 0.0);
            row.put("valueText", boolVal ? "true" : "false");
        } else if (curVal instanceof Number number) {
            row.put("value", number.doubleValue());
            row.put("valueText", number.toString());
        } else {
            row.put("value", 0.0);
            row.put("valueText", curVal == null ? "" : curVal.toString());
        }
        return DataRecord.single(POINT_SCHEMA, row);
    }
}
