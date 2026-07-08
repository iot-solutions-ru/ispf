package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.mimic.MimicDiagramJsonSupport;
import com.ispf.server.mimic.MimicService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agent tools for SCADA MIMIC objects — read/write diagramJson with elements and bindings.
 */
final class AgentMimicTools {

    private AgentMimicTools() {
    }

    static List<PlatformAgentTool> all(
            MimicService mimicService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return List.of(
                getMimicDiagramTool(mimicService, objectAccessService, tenantScopeService, objectMapper),
                saveMimicDiagramTool(mimicService, objectManager, objectAccessService, tenantScopeService, objectMapper),
                addMimicElementsTool(mimicService, objectManager, objectAccessService, tenantScopeService, objectMapper),
                listMimicSymbolsTool()
        );
    }

    private static PlatformAgentTool getMimicDiagramTool(
            MimicService mimicService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_mimic_diagram";
            }

            @Override
            public String description() {
                return "Read SCADA mimic diagramJson for a MIMIC object. Args: path. "
                        + "Returns elementCount, connectionCount, title, and diagram (parsed object).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireRead(path, auth);
                try {
                    MimicService.MimicView view = mimicService.getMimic(path);
                    int elementCount = MimicDiagramJsonSupport.countElements(view.diagramJson(), objectMapper);
                    int connectionCount = MimicDiagramJsonSupport.countConnections(view.diagramJson(), objectMapper);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "OK");
                    response.put("path", view.path());
                    response.put("title", view.title());
                    response.put("refreshIntervalMs", view.refreshIntervalMs());
                    response.put("elementCount", elementCount);
                    response.put("connectionCount", connectionCount);
                    response.put("diagram", objectMapper.readValue(view.diagramJson(), Map.class));
                    if (elementCount == 0) {
                        response.put(
                                "hint",
                                "Diagram is empty. Call save_mimic_diagram or add_mimic_elements with non-empty elements[] "
                                        + "before finishing. Use list_mimic_symbols for symbolId values."
                        );
                    }
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool saveMimicDiagramTool(
            MimicService mimicService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "save_mimic_diagram";
            }

            @Override
            public String description() {
                return "Save SCADA mimic diagramJson on a MIMIC object. REQUIRED after create_object type=MIMIC. "
                        + "Args: path; diagramJson (full v2 document string or object) OR elements[] "
                        + "(+ optional connections[], width, height, devicePath for binding shorthand). "
                        + "Optional merge=true appends elements instead of replacing. "
                        + "Never use set_variable name=diagram — use this tool. "
                        + "Bindings: {slot:{objectPath,variableName,valueField,transform}}.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                return persistDiagram(arguments, context, false, mimicService, objectManager,
                        objectAccessService, tenantScopeService, objectMapper);
            }
        };
    }

    private static PlatformAgentTool addMimicElementsTool(
            MimicService mimicService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "add_mimic_elements";
            }

            @Override
            public String description() {
                return "Append SCADA symbols to an existing mimic. Args: path, elements[] "
                        + "(required, non-empty), optional connections[]. "
                        + "Each element: id, symbolId, layerId=layer-default, x, y, bindings, props.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                Map<String, Object> args = new LinkedHashMap<>(arguments);
                args.put("merge", true);
                return persistDiagram(args, context, true, mimicService, objectManager,
                        objectAccessService, tenantScopeService, objectMapper);
            }
        };
    }

    private static Map<String, Object> persistDiagram(
            Map<String, Object> arguments,
            AgentContext context,
            boolean requireElements,
            MimicService mimicService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        String path = stringArg(arguments, "path");
        if (path.isBlank()) {
            return Map.of("status", "ERROR", "error", "path is required");
        }
        var auth = context.authentication();
        if (!tenantScopeService.isPathVisible(path, auth)) {
            return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
        }
        objectAccessService.requireWrite(path, auth);
        try {
            PlatformObject node = objectManager.require(path);
            if (node.type() != ObjectType.MIMIC) {
                return Map.of("status", "ERROR", "error", "Not a MIMIC object: " + path);
            }
            Object diagramArg = firstPresent(arguments, "diagramJson", "diagram");
            Object elementsArg = arguments.get("elements");
            Object connectionsArg = arguments.get("connections");
            boolean merge = Boolean.TRUE.equals(arguments.get("merge"));
            String devicePath = stringArg(arguments, "devicePath");
            if (elementsArg != null) {
                elementsArg = enrichElements(elementsArg, devicePath, objectMapper);
            }
            String diagramJson;
            if (diagramArg != null) {
                diagramJson = MimicDiagramJsonSupport.normalize(diagramArg, objectMapper);
            } else if (elementsArg != null) {
                int incomingCount = toElementCount(elementsArg, objectMapper);
                if (!merge && incomingCount == 0) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "elements must be a non-empty array",
                            "hint", "Each element needs id, symbolId, layerId, x, y. "
                                    + "Use list_mimic_symbols. For pump.centrifugal bind running/fault to "
                                    + "lab profile sineWave (vibration proxy) via devicePath + variableName."
                    );
                }
                if (requireElements && incomingCount == 0) {
                    return Map.of("status", "ERROR", "error", "elements must be a non-empty array");
                }
                MimicService.MimicView current = mimicService.getMimic(path);
                if (merge) {
                    diagramJson = MimicDiagramJsonSupport.mergeElements(
                            current.diagramJson(), elementsArg, connectionsArg, true, objectMapper);
                } else {
                    diagramJson = MimicDiagramJsonSupport.replaceElements(
                            current.diagramJson(), elementsArg, connectionsArg, objectMapper);
                }
                if (arguments.containsKey("width") || arguments.containsKey("height")) {
                    var doc = MimicDiagramJsonSupport.parseToObjectNode(diagramJson, objectMapper);
                    if (arguments.containsKey("width")) {
                        doc.put("width", numberArg(arguments, "width", 1600));
                    }
                    if (arguments.containsKey("height")) {
                        doc.put("height", numberArg(arguments, "height", 900));
                    }
                    diagramJson = MimicDiagramJsonSupport.normalize(doc, objectMapper);
                }
            } else {
                return Map.of(
                        "status", "ERROR",
                        "error",
                        "diagramJson or elements is required. Example element: "
                                + "{\"id\":\"p1\",\"symbolId\":\"pump.centrifugal\",\"layerId\":\"layer-default\","
                                + "\"x\":200,\"y\":120,\"bindings\":{\"running\":{"
                                + "\"objectPath\":\"<devicePath>\",\"variableName\":\"sineWave\","
                                + "\"valueField\":\"value\",\"transform\":\"bool\"}}}"
                );
            }
            int normalizedCount = MimicDiagramJsonSupport.countElements(diagramJson, objectMapper);
            if (!merge && normalizedCount == 0) {
                return Map.of(
                        "status", "ERROR",
                        "error", "Cannot save empty mimic diagram (elementCount=0)",
                        "hint", "Provide non-empty elements[] with symbolId, x, y, and bindings. "
                                + "Call list_mimic_symbols and list_variables on the device first."
                );
            }
            MimicService.MimicView saved = mimicService.saveDiagram(path, diagramJson);
            int elementCount = MimicDiagramJsonSupport.countElements(saved.diagramJson(), objectMapper);
            int connectionCount = MimicDiagramJsonSupport.countConnections(saved.diagramJson(), objectMapper);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "OK");
            response.put("path", saved.path());
            response.put("title", saved.title());
            response.put("elementCount", elementCount);
            response.put("connectionCount", connectionCount);
            if (elementCount == 0) {
                response.put(
                        "warning",
                        "Mimic saved but elements[] is still empty — add tanks/valves/pipes with save_mimic_diagram or add_mimic_elements"
                );
            }
            return response;
        } catch (Exception ex) {
            return Map.of("status", "ERROR", "error", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Object enrichElements(Object elementsArg, String devicePath, ObjectMapper objectMapper) {
        if (!(elementsArg instanceof List<?> list) || list.isEmpty()) {
            return elementsArg;
        }
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> element = new LinkedHashMap<>((Map<String, Object>) rawMap);
            Object bindingsRaw = element.get("bindings");
            if (bindingsRaw instanceof Map<?, ?> bindingsMap) {
                Map<String, Object> bindings = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : bindingsMap.entrySet()) {
                    String slot = String.valueOf(entry.getKey());
                    bindings.put(slot, enrichBindingSlot(slot, entry.getValue(), devicePath));
                }
                element.put("bindings", bindings);
            }
            enriched.add(element);
        }
        return enriched;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> enrichBindingSlot(String slot, Object rawBinding, String devicePath) {
        if (rawBinding instanceof Map<?, ?> bindingMap) {
            Map<String, Object> binding = new LinkedHashMap<>((Map<String, Object>) bindingMap);
            if ((!binding.containsKey("objectPath") || stringValue(binding.get("objectPath")).isBlank())
                    && !devicePath.isBlank()) {
                binding.put("objectPath", devicePath);
            }
            if (!binding.containsKey("valueField") || stringValue(binding.get("valueField")).isBlank()) {
                binding.put("valueField", "value");
            }
            String variableName = stringValue(binding.get("variableName"));
            if ("vibration".equalsIgnoreCase(variableName)) {
                binding.put("variableName", "sineWave");
            }
            if (!binding.containsKey("transform") || stringValue(binding.get("transform")).isBlank()) {
                if ("running".equals(slot) || "fault".equals(slot) || "open".equals(slot) || "closed".equals(slot)) {
                    binding.put("transform", "bool");
                } else if ("fillLevel".equals(slot) || "value".equals(slot) || "maxLevel".equals(slot)) {
                    binding.put("transform", "number");
                }
            }
            return binding;
        }
        if (rawBinding instanceof String variableName && !variableName.isBlank()) {
            Map<String, Object> binding = new LinkedHashMap<>();
            if (!devicePath.isBlank()) {
                binding.put("objectPath", devicePath);
            }
            String normalized = "vibration".equalsIgnoreCase(variableName) ? "sineWave" : variableName.trim();
            binding.put("variableName", normalized);
            binding.put("valueField", "value");
            binding.put("transform", isBoolSlot(slot) ? "bool" : "number");
            return binding;
        }
        return Map.of();
    }

    private static boolean isBoolSlot(String slot) {
        return "running".equals(slot) || "fault".equals(slot) || "open".equals(slot) || "closed".equals(slot);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static PlatformAgentTool listMimicSymbolsTool() {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_mimic_symbols";
            }

            @Override
            public String description() {
                return "Catalog of built-in SCADA symbolId values for diagram elements. "
                        + "Optional arg: category (process|pipe|sensor|electrical|annotation|all).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String category = stringArg(arguments, "category").toLowerCase(Locale.ROOT);
                if (category.isBlank()) {
                    category = "all";
                }
                List<Map<String, Object>> symbols = MimicSymbolCatalog.symbols(category);
                return Map.of(
                        "status", "OK",
                        "category", category,
                        "count", symbols.size(),
                        "symbols", symbols,
                        "layerId", MimicDiagramJsonSupport.DEFAULT_LAYER_ID,
                        "exampleElement", MimicSymbolCatalog.exampleElement()
                );
            }
        };
    }

    private static Object firstPresent(Map<String, Object> arguments, String... keys) {
        for (String key : keys) {
            if (arguments.containsKey(key) && arguments.get(key) != null) {
                return arguments.get(key);
            }
        }
        return null;
    }

    private static int toElementCount(Object elementsArg, ObjectMapper objectMapper) {
        try {
            var doc = objectMapper.createObjectNode();
            doc.set("elements", objectMapper.valueToTree(elementsArg));
            return MimicDiagramJsonSupport.countElements(
                    MimicDiagramJsonSupport.normalize(doc, objectMapper),
                    objectMapper
            );
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static int numberArg(Map<String, Object> arguments, String key, int fallback) {
        Object raw = arguments.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return fallback;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
