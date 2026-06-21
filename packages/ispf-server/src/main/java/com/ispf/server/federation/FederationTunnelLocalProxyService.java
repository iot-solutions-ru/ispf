package com.ispf.server.federation;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.api.dto.DataRecordPayloadResolver;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.server.api.dto.VariableDto;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectUiIconService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FederationTunnelLocalProxyService {

    private final ObjectManager objectManager;
    private final ObjectUiIconService objectUiIconService;
    private final FunctionService functionService;
    private final DashboardService dashboardService;
    private final VariableHistoryService variableHistoryService;
    private final ObjectMapper objectMapper;

    public FederationTunnelLocalProxyService(
            ObjectManager objectManager,
            ObjectUiIconService objectUiIconService,
            FunctionService functionService,
            DashboardService dashboardService,
            VariableHistoryService variableHistoryService,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.objectUiIconService = objectUiIconService;
        this.functionService = functionService;
        this.dashboardService = dashboardService;
        this.variableHistoryService = variableHistoryService;
        this.objectMapper = objectMapper;
    }

    public FederationTunnelLocalProxyResult dispatch(String method, String path, String query, String bodyJson) {
        String normalizedPath = path == null ? "" : path.trim();
        Map<String, String> params = parseQuery(query);
        try {
            return switch (normalizedPath) {
                case "/api/v1/objects" -> result(200, objectMapper.valueToTree(listObjects()));
                case "/api/v1/objects/by-path" -> handleObjectByPath(method, params);
                case "/api/v1/objects/by-path/variables" -> handleVariables(method, params);
                case "/api/v1/objects/by-path/variables/value" -> handleVariableValuePatch(params, bodyJson);
                case "/api/v1/objects/by-path/functions/invoke" -> handleFunctionInvoke(params, bodyJson);
                case "/api/v1/objects/by-path/variables/history" -> handleVariableHistory(params, false);
                case "/api/v1/objects/by-path/variables/history/aggregate" -> handleVariableHistory(params, true);
                case "/api/v1/dashboards/by-path" -> handleDashboard(params);
                default -> error(HttpStatus.NOT_FOUND, "Unsupported tunnel proxy path: " + normalizedPath);
            };
        } catch (ResponseStatusException ex) {
            return error(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private FederationTunnelLocalProxyResult handleObjectByPath(String method, Map<String, String> params) {
        if (!"GET".equalsIgnoreCase(method)) {
            return error(HttpStatus.METHOD_NOT_ALLOWED, "Only GET supported");
        }
        String objectPath = requiredParam(params, "path");
        PlatformObject node = objectManager.require(objectPath);
        ObjectDto dto = ObjectDto.from(node, objectUiIconService.readIconId(node).orElse(null));
        return result(200, objectMapper.valueToTree(dto));
    }

    private FederationTunnelLocalProxyResult handleVariables(String method, Map<String, String> params) {
        if (!"GET".equalsIgnoreCase(method)) {
            return error(HttpStatus.METHOD_NOT_ALLOWED, "Only GET supported");
        }
        String objectPath = requiredParam(params, "path");
        PlatformObject node = objectManager.require(objectPath);
        List<VariableDto> variables = node.variables().values().stream().map(VariableDto::from).toList();
        return result(200, objectMapper.valueToTree(variables));
    }

    private FederationTunnelLocalProxyResult handleVariableValuePatch(Map<String, String> params, String bodyJson) {
        String objectPath = requiredParam(params, "path");
        String name = requiredParam(params, "name");
        DataRecordPayloadRequest payload = bodyJson == null || bodyJson.isBlank()
                ? null
                : objectMapper.readValue(bodyJson, DataRecordPayloadRequest.class);
        PlatformObject node = objectManager.require(objectPath);
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable: " + name));
        DataRecord resolved = DataRecordPayloadResolver.resolve(variable.schema(), payload);
        Variable updated = objectManager.setVariableValue(objectPath, name, resolved);
        return result(200, objectMapper.valueToTree(VariableDto.from(updated)));
    }

    private FederationTunnelLocalProxyResult handleFunctionInvoke(Map<String, String> params, String bodyJson) {
        String objectPath = requiredParam(params, "path");
        String functionName = requiredParam(params, "name");
        DataRecordPayloadRequest payload = bodyJson == null || bodyJson.isBlank()
                ? null
                : objectMapper.readValue(bodyJson, DataRecordPayloadRequest.class);
        DataRecord result = functionService.invoke(objectPath, functionName, payload);
        return result(200, objectMapper.valueToTree(result));
    }

    private FederationTunnelLocalProxyResult handleVariableHistory(Map<String, String> params, boolean aggregate) {
        String objectPath = requiredParam(params, "path");
        String name = requiredParam(params, "name");
        String field = params.getOrDefault("field", "value");
        int limit = parseInt(params.get("limit"), 500);
        Instant from = params.containsKey("from") ? Instant.parse(params.get("from")) : null;
        Instant to = params.containsKey("to") ? Instant.parse(params.get("to")) : null;
        if (aggregate) {
            String bucket = requiredParam(params, "bucket");
            var response = variableHistoryService.aggregate(objectPath, name, field, from, to, bucket, limit);
            return result(200, objectMapper.valueToTree(response));
        }
        var response = variableHistoryService.query(objectPath, name, field, from, to, limit);
        return result(200, objectMapper.valueToTree(response));
    }

    private FederationTunnelLocalProxyResult handleDashboard(Map<String, String> params) {
        String objectPath = requiredParam(params, "path");
        var dashboard = dashboardService.getDashboard(objectPath);
        return result(200, objectMapper.valueToTree(dashboard));
    }

    private List<ObjectDto> listObjects() {
        return objectManager.tree().all().stream()
                .map(node -> ObjectDto.from(node, objectUiIconService.readIconId(node).orElse(null)))
                .toList();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String part : query.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq < 0) {
                params.put(decode(part), "");
            } else {
                params.put(decode(part.substring(0, eq)), decode(part.substring(eq + 1)));
            }
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String requiredParam(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing query param: " + name);
        }
        return value;
    }

    private static int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(raw);
    }

    private FederationTunnelLocalProxyResult result(int status, JsonNode body) {
        return new FederationTunnelLocalProxyResult(status, body, null);
    }

    private FederationTunnelLocalProxyResult error(HttpStatus status, String message) {
        return new FederationTunnelLocalProxyResult(status.value(), null, message);
    }

    public record FederationTunnelLocalProxyResult(int status, JsonNode body, String error) {
    }
}
