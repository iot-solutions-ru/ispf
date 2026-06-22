package com.ispf.server.application.catalog;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.IspfSecurityProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ApplicationEventCatalogService {

    private final ApplicationEventCatalogStore store;
    private final ObjectMapper objectMapper;
    private final IspfSecurityProperties securityProperties;

    public ApplicationEventCatalogService(
            ApplicationEventCatalogStore store,
            ObjectMapper objectMapper,
            IspfSecurityProperties securityProperties
    ) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.securityProperties = securityProperties;
    }

    public void replaceFromBundle(String appId, List<BundleEventDefinition> events) {
        if (events == null || events.isEmpty()) {
            store.replaceForApp(appId, List.of());
            return;
        }
        List<ApplicationEventCatalogStore.EventCatalogEntry> entries = new ArrayList<>();
        for (BundleEventDefinition event : events) {
            if (event.id() == null || event.id().isBlank()) {
                throw new IllegalArgumentException("Event catalog entry requires id");
            }
            List<String> roles = event.roles() != null ? event.roles() : List.of();
            String payloadSchemaJson = event.payloadSchema() != null
                    ? writeJson(event.payloadSchema())
                    : null;
            entries.add(new ApplicationEventCatalogStore.EventCatalogEntry(
                    event.id(),
                    writeJson(roles),
                    payloadSchemaJson
            ));
        }
        store.replaceForApp(appId, entries);
    }

    public List<Map<String, Object>> listEvents(String appId) {
        List<Map<String, Object>> rows = store.listForApp(appId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("roles", readRoles(String.valueOf(row.get("rolesJson"))));
            Object schema = row.get("payloadSchemaJson");
            if (schema != null && !String.valueOf(schema).isBlank()) {
                item.put("payloadSchema", readJson(String.valueOf(schema)));
            }
            item.put("updatedAt", row.get("updatedAt"));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> filterSubscribableEvents(String appId, List<String> eventIds, List<String> userRoles) {
        List<String> accepted = new ArrayList<>();
        List<Map<String, String>> rejected = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String eventId : eventIds) {
            if (eventId == null || eventId.isBlank()) {
                continue;
            }
            unique.add(eventId.trim());
        }
        for (String eventId : unique) {
            if (canSubscribe(appId, eventId, userRoles)) {
                accepted.add(eventId);
            } else {
                rejected.add(Map.of("event", eventId, "reason", "FORBIDDEN"));
            }
        }
        return Map.of("accepted", accepted, "rejected", rejected);
    }

    public boolean canSubscribe(String appId, String eventId, List<String> userRoles) {
        if (!securityProperties.isRbacEnabled()) {
            return true;
        }
        if (hasAdminRole(userRoles)) {
            return true;
        }
        return store.find(appId, eventId)
                .map(entry -> rolesAllow(entry.rolesJson(), userRoles))
                .orElse(true);
    }

    private boolean hasAdminRole(List<String> userRoles) {
        return userRoles != null && userRoles.stream().anyMatch(IspfRoles.ADMIN::equalsIgnoreCase);
    }

    private boolean rolesAllow(String rolesJson, List<String> userRoles) {
        List<String> required = readRoles(rolesJson);
        if (required.isEmpty()) {
            return true;
        }
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        for (String role : userRoles) {
            for (String requiredRole : required) {
                if (requiredRole.equalsIgnoreCase(role)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> readRoles(String rolesJson) {
        try {
            return objectMapper.readValue(rolesJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Object readJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return json;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize event catalog JSON: " + ex.getMessage(), ex);
        }
    }

    public record BundleEventDefinition(String id, List<String> roles, Object payloadSchema) {
    }
}
