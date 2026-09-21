package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.PlatformObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BlueprintParameterResolver {

    public Map<String, String> parametersFor(PlatformObject self, Map<String, String> explicit) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (self != null) {
            parameters.put("self.path", self.path());
            parameters.put("self.name", leafName(self.path()));
            parameters.put("self.displayName", self.displayName());
        }
        if (explicit != null) {
            explicit.forEach((key, value) -> {
                if (key != null && value != null) {
                    parameters.put(key, value);
                }
            });
        }
        return parameters;
    }

    public String resolve(String expression, Map<String, String> parameters) {
        if (expression == null || parameters == null || parameters.isEmpty()) {
            return expression;
        }
        String resolved = expression;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            resolved = resolved.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }

    public Map<String, String> resolveValues(Map<String, String> values, Map<String, String> parameters) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        values.forEach((key, value) -> resolved.put(key, resolve(value, parameters)));
        return resolved;
    }

    private static String leafName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
