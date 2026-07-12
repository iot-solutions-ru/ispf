package com.ispf.server.query;

import com.ispf.core.ref.PlatformRefParser;
import com.ispf.server.ref.PlatformRefExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObjectQueryPatchService {

    private final ObjectMapper objectMapper;
    private final PlatformRefExecutor platformRefExecutor;

    public ObjectQueryPatchService(ObjectMapper objectMapper, PlatformRefExecutor platformRefExecutor) {
        this.objectMapper = objectMapper;
        this.platformRefExecutor = platformRefExecutor;
    }

    public PatchResult apply(String patchJson, String callerObjectPath) {
        if (patchJson == null || patchJson.isBlank()) {
            return new PatchResult(0, 0, List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(patchJson.trim());
            List<Map<String, Object>> patches = parsePatches(root);
            int applied = 0;
            int failed = 0;
            List<String> errors = new ArrayList<>();
            for (Map<String, Object> patch : patches) {
                Object refRaw = patch.get("ref");
                if (refRaw == null || String.valueOf(refRaw).isBlank()) {
                    failed++;
                    errors.add("patch missing ref");
                    continue;
                }
                try {
                    if (platformRefExecutor.write(
                            PlatformRefParser.parseVariableSource(String.valueOf(refRaw)),
                            patch.get("value"),
                            callerObjectPath
                    )) {
                        applied++;
                    } else {
                        failed++;
                        errors.add("write failed: " + refRaw);
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    errors.add(ex.getMessage() != null ? ex.getMessage() : "write error");
                }
            }
            return new PatchResult(applied, failed, List.copyOf(errors));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid OQ patch JSON: " + ex.getMessage(), ex);
        }
    }

    private static List<Map<String, Object>> parsePatches(JsonNode root) {
        JsonNode array = root;
        if (root.isObject() && root.has("patches")) {
            array = root.get("patches");
        }
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("patch must be a JSON array or {patches:[]}");
        }
        List<Map<String, Object>> patches = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isObject()) {
                continue;
            }
            Map<String, Object> patch = new LinkedHashMap<>();
            if (item.hasNonNull("ref")) {
                patch.put("ref", item.get("ref").asString());
            }
            if (item.has("value")) {
                JsonNode value = item.get("value");
                patch.put("value", value.isNull() ? null : value.isNumber() ? value.numberValue() : value.asString());
            }
            patches.add(patch);
        }
        return patches;
    }

    public record PatchResult(int applied, int failed, List<String> errors) {
    }
}
