package com.ispf.driver.graphql;

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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GraphQL driver — HTTP POST queries and optional mutations against a GraphQL endpoint.
 * <p>
 * Point mapping is a GraphQL query document, a dotted field path under {@code data}
 * (with configuration {@code query}), or {@code document >> field.path}. Writes POST a
 * mutation from the mapping or from the record {@code mutation}/{@code query} field.
 * Clean-room ISPF code, Apache-2.0 — JDK {@link HttpClient} only (no graphql-java).
 */
public class GraphqlDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("graphqlValue")
            .field("value", FieldType.STRING)
            .field("statusCode", FieldType.INTEGER)
            .field("path", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "graphql",
            "GraphQL Driver",
            "0.1.0",
            "POSTs GraphQL queries (and optional mutations) over HTTP and maps data fields to points",
            "ISPF",
            Map.of(
                    "endpoint", "http://127.0.0.1:8080/graphql",
                    "query", "",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "10000"
            ),
            DriverMaturity.PRODUCTION,
            Set.of("read", "write")
    );

    private DriverObject driverObject;
    private HttpClient client;
    private String endpoint = "http://127.0.0.1:8080/graphql";
    private String defaultQuery = "";
    private long timeoutMs = 5000;
    private final Map<String, GraphqlPoint> points = new ConcurrentHashMap<>();
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
            case "endpoint", "baseUrl", "url" -> endpoint = value.trim();
            case "query" -> defaultQuery = value.trim();
            case "timeoutMs" -> timeoutMs = Long.parseLong(value.trim());
            default -> { }
        }
    }

    @Override
    public void connect() throws DriverException {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "GraphQL client ready (endpoint=" + endpoint + ")");
    }

    @Override
    public void disconnect() {
        connected = false;
        client = null;
        points.clear();
        if (driverObject != null) {
            driverObject.log(DriverLogLevel.INFO, "GraphQL client disconnected");
        }
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
            String pointId = entry.getKey();
            String mapping = entry.getValue() == null || entry.getValue().isBlank() ? pointId : entry.getValue();
            GraphqlPoint point;
            try {
                point = GraphqlPoint.parse(mapping, defaultQuery);
            } catch (IllegalArgumentException e) {
                throw new DriverException("Invalid GraphQL point mapping for " + pointId + ": " + e.getMessage(), e);
            }
            points.put(pointId, point);
            HttpResponse<String> response = post(point.document(), null);
            String body = response.body() == null ? "" : response.body();
            String value = extractValue(body, point.fieldPath());
            driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "value", value,
                    "statusCode", response.statusCode(),
                    "path", point.fieldPath() == null ? "" : point.fieldPath()
            )));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DriverException("GraphQL query failed for " + pointId + ": HTTP " + response.statusCode());
            }
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
        if (value == null || value.rowCount() == 0) {
            throw new DriverException("GraphQL write requires a non-empty DataRecord");
        }
        Map<String, Object> row = value.firstRow();
        String mutation = firstString(row, "mutation", "query");
        GraphqlPoint mapped = points.get(pointId);
        if (mutation == null || mutation.isBlank()) {
            if (mapped != null && GraphqlPoint.looksLikeDocument(mapped.document())
                    && mapped.document().toLowerCase().trim().startsWith("mutation")) {
                mutation = mapped.document();
            } else if (mapped != null) {
                mutation = mapped.document();
            } else {
                throw new DriverException("GraphQL write needs a mutation mapping or record mutation/query field");
            }
        }
        String variablesJson = firstString(row, "variables");
        if (variablesJson == null) {
            Map<String, Object> vars = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if ("mutation".equals(key) || "query".equals(key) || "variables".equals(key)
                        || "statusCode".equals(key) || "path".equals(key) || "value".equals(key)) {
                    continue;
                }
                if (entry.getValue() != null) {
                    vars.put(key, entry.getValue());
                }
            }
            Object direct = row.get("value");
            if (vars.isEmpty() && direct != null) {
                vars.put("value", direct);
            }
            variablesJson = vars.isEmpty() ? null : toJsonObject(vars);
        }
        HttpResponse<String> response = post(mutation, variablesJson);
        String body = response.body() == null ? "" : response.body();
        String fieldPath = mapped == null ? null : mapped.fieldPath();
        String extracted = extractValue(body, fieldPath);
        driverObject.updateVariable(pointId, DataRecord.single(VALUE_SCHEMA, Map.of(
                "value", extracted,
                "statusCode", response.statusCode(),
                "path", fieldPath == null ? "" : fieldPath
        )));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DriverException("GraphQL mutation failed for " + pointId + ": HTTP " + response.statusCode());
        }
    }

    private HttpResponse<String> post(String document, String variablesJson) throws DriverException {
        StringBuilder json = new StringBuilder("{\"query\":\"");
        json.append(escapeJson(document)).append('"');
        if (variablesJson != null && !variablesJson.isBlank()) {
            json.append(",\"variables\":");
            String trimmed = variablesJson.trim();
            if (trimmed.startsWith("{")) {
                json.append(trimmed);
            } else {
                json.append('"').append(escapeJson(trimmed)).append('"');
            }
        }
        json.append('}');
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new DriverException("GraphQL HTTP POST failed for " + endpoint, e);
        }
    }

    static String extractValue(String body, String fieldPath) {
        if (body == null) {
            return "";
        }
        String dataJson = extractJsonObject(body, "data");
        if (dataJson == null) {
            return body.trim();
        }
        if (fieldPath == null || fieldPath.isBlank()) {
            return dataJson;
        }
        String current = dataJson;
        for (String segment : fieldPath.split("\\.")) {
            if (segment.isBlank()) {
                continue;
            }
            String nested = extractJsonObject(current, segment);
            if (nested != null) {
                current = nested;
                continue;
            }
            String scalar = extractJsonScalar(current, segment);
            return scalar == null ? "" : scalar;
        }
        return current;
    }

    static String extractJsonObject(String json, String field) {
        int idx = indexOfField(json, field);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int i = skipWs(json, colon + 1);
        if (i >= json.length() || (json.charAt(i) != '{' && json.charAt(i) != '[')) {
            return null;
        }
        char open = json.charAt(i);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int j = i; j < json.length(); j++) {
            char c = json.charAt(j);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return json.substring(i, j + 1);
                }
            }
        }
        return null;
    }

    static String extractJsonScalar(String json, String field) {
        int idx = indexOfField(json, field);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int i = skipWs(json, colon + 1);
        if (i >= json.length()) {
            return null;
        }
        char start = json.charAt(i);
        if (start == '"') {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < json.length()) {
                char c = json.charAt(i++);
                if (c == '\\' && i < json.length()) {
                    sb.append(json.charAt(i++));
                    continue;
                }
                if (c == '"') {
                    break;
                }
                sb.append(c);
            }
            return sb.toString();
        }
        if (start == '{' || start == '[') {
            return extractJsonObject(json, field);
        }
        int end = i;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                break;
            }
            end++;
        }
        return json.substring(i, end);
    }

    private static int indexOfField(String json, String field) {
        if (json == null || field == null) {
            return -1;
        }
        String needle = "\"" + field + "\"";
        int from = 0;
        while (from < json.length()) {
            int idx = json.indexOf(needle, from);
            if (idx < 0) {
                return -1;
            }
            int after = idx + needle.length();
            int colon = skipWs(json, after);
            if (colon < json.length() && json.charAt(colon) == ':') {
                return idx;
            }
            from = after;
        }
        return -1;
    }

    private static int skipWs(String text, int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String firstString(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static String toJsonObject(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            appendJsonValue(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
            return;
        }
        String text = String.valueOf(value).trim();
        if (("true".equals(text) || "false".equals(text) || "null".equals(text))
                || text.matches("-?\\d+(\\.\\d+)?")) {
            sb.append(text);
            return;
        }
        if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
            sb.append(text);
            return;
        }
        sb.append('"').append(escapeJson(text)).append('"');
    }

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
