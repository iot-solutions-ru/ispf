package com.ispf.server.application.catalog;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * FW-31 tail: validate event fire payload against bundle catalog {@code payloadSchema}
 * (JSON Schema subset: type object, required, properties types).
 */
@Component
public class EventCatalogPayloadValidator {

    private final ApplicationEventCatalogStore store;
    private final ObjectMapper objectMapper;

    public EventCatalogPayloadValidator(ApplicationEventCatalogStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public void validateAtFire(String appId, String eventId, DataRecordPayloadRequest payload) {
        if (appId == null || appId.isBlank()) {
            return;
        }
        ApplicationEventCatalogStore.EventCatalogEntry entry = store.find(appId, eventId).orElse(null);
        if (entry == null || entry.payloadSchemaJson() == null || entry.payloadSchemaJson().isBlank()) {
            return;
        }
        Map<String, Object> schema = readSchema(entry.payloadSchemaJson());
        if (schema.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = payload != null ? payload.rows() : null;
        if (rows == null || rows.isEmpty() || rows.stream().allMatch(Map::isEmpty)) {
            assertRequiredPresent(schema, Map.of());
            return;
        }
        for (Map<String, Object> row : rows) {
            assertRequiredPresent(schema, row != null ? row : Map.of());
            assertPropertyTypes(schema, row != null ? row : Map.of());
        }
    }

    private Map<String, Object> readSchema(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid event payloadSchema in catalog: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void assertRequiredPresent(Map<String, Object> schema, Map<String, Object> row) {
        Object requiredObj = schema.get("required");
        if (!(requiredObj instanceof List<?> required)) {
            return;
        }
        for (Object item : required) {
            String key = String.valueOf(item);
            if (!row.containsKey(key) || row.get(key) == null) {
                throw new IllegalArgumentException(
                        "Event payload missing required field '" + key + "' per catalog payloadSchema");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void assertPropertyTypes(Map<String, Object> schema, Map<String, Object> row) {
        Object propertiesObj = schema.get("properties");
        if (!(propertiesObj instanceof Map<?, ?> properties)) {
            return;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object propSchemaObj = properties.get(entry.getKey());
            if (!(propSchemaObj instanceof Map<?, ?> propSchema)) {
                continue;
            }
            Object typeObj = propSchema.get("type");
            if (typeObj == null) {
                continue;
            }
            String type = String.valueOf(typeObj);
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (!matchesJsonType(type, value)) {
                throw new IllegalArgumentException(
                        "Event payload field '" + entry.getKey() + "' expected type " + type);
            }
        }
    }

    private boolean matchesJsonType(String type, Object value) {
        return switch (type) {
            case "string" -> value instanceof String;
            case "number", "integer" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            default -> true;
        };
    }
}
