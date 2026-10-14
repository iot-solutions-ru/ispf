package com.ispf.server.ai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SCADA symbol reference for the platform agent.
 * Only IDs that the web-console can resolve (legacy builtins mapped to ispf-pid-v1 pack,
 * or pack.* ids). Do not advertise removed React-only symbols (label, status-arrow, button.*).
 */
final class MimicSymbolCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MimicSymbolCatalog() {
    }

    /**
     * True when the web-console can resolve the symbol (catalog legacy id, pack SVG, or custom).
     * Unknown ids (label, button.start, …) render as an empty canvas.
     */
    static boolean isRenderable(String symbolId) {
        if (symbolId == null || symbolId.isBlank()) {
            return false;
        }
        String id = symbolId.trim();
        for (SymbolRef symbol : SYMBOLS) {
            if (symbol.id.equals(id)) {
                return true;
            }
        }
        String lower = id.toLowerCase(Locale.ROOT);
        if ("custom.svg".equals(lower) || lower.startsWith("custom:")) {
            return true;
        }
        // Any installed ispf-pid pack id is loadable even if not listed in the short agent catalog.
        return lower.startsWith("pack.ispf-pid.");
    }

    /** Unknown / blank symbolIds from a mimic diagramJson (v2). */
    static List<String> unknownSymbolIds(String diagramJson) {
        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        if (diagramJson == null || diagramJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode elements = MAPPER.readTree(diagramJson).path("elements");
            if (!elements.isArray()) {
                return List.of();
            }
            for (JsonNode element : elements) {
                String id = element.path("symbolId").asString("").trim();
                if (!isRenderable(id)) {
                    unknown.add(id.isBlank() ? "(blank)" : id);
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.copyOf(unknown);
    }

    static List<Map<String, Object>> symbols(String categoryFilter) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SymbolRef symbol : SYMBOLS) {
            if (!"all".equals(categoryFilter) && !symbol.category.equals(categoryFilter)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", symbol.id);
            row.put("category", symbol.category);
            row.put("bindings", symbol.bindings);
            row.put("defaultWidth", symbol.defaultWidth);
            row.put("defaultHeight", symbol.defaultHeight);
            out.add(row);
        }
        return out;
    }

    static Map<String, Object> exampleElement() {
        Map<String, Object> binding = Map.of(
                "objectPath", "<devicePath>",
                "variableName", "<variableName>",
                "valueField", "value",
                "transform", "number"
        );
        return Map.of(
                "id", "tank-1",
                "symbolId", "tank.vertical",
                "layerId", "layer-default",
                "x", 100,
                "y", 80,
                "props", Map.of("width", 80, "height", 120),
                "bindings", Map.of("fillLevel", binding)
        );
    }

    private record SymbolRef(
            String id,
            String category,
            List<String> bindings,
            int defaultWidth,
            int defaultHeight
    ) {
    }

    private static final List<SymbolRef> SYMBOLS = List.of(
            // Legacy ids (web-console maps these to pack.ispf-pid.*)
            sym("tank.vertical", "process", List.of("fillLevel", "maxLevel", "rate"), 80, 120),
            sym("tank.horizontal", "process", List.of("fillLevel", "maxLevel"), 120, 80),
            sym("tank.spherical", "process", List.of("fillLevel"), 80, 80),
            sym("valve.butterfly", "process", List.of("open", "intermediate"), 40, 60),
            sym("valve.gate", "process", List.of("open"), 36, 56),
            sym("valve.check", "process", List.of("open"), 36, 36),
            sym("valve.ball", "process", List.of("open"), 36, 56),
            sym("valve.globe", "process", List.of("open"), 36, 56),
            sym("pump.centrifugal", "process", List.of("running", "fault"), 56, 56),
            sym("pump.positive", "process", List.of("running"), 56, 56),
            sym("pump.submersible", "process", List.of("running"), 56, 56),
            sym("motor", "process", List.of("running"), 64, 64),
            sym("compressor", "process", List.of("running"), 56, 56),
            sym("mixer", "process", List.of("running"), 56, 56),
            sym("pipe.segment", "pipe", List.of(), 80, 12),
            sym("pipe.junction", "pipe", List.of(), 24, 24),
            sym("pipe.tee", "pipe", List.of(), 40, 40),
            sym("gen.block", "electrical", List.of("running"), 56, 56),
            sym("breaker", "electrical", List.of("closed"), 40, 56),
            sym("transformer.two-winding", "electrical", List.of(), 56, 72),
            // Prefer pack ids for fan / push-button (no legacy React builtin)
            sym("pack.ispf-pid.fan", "process", List.of("running"), 64, 64),
            sym("pack.ispf-pid.motor", "electrical", List.of("running"), 64, 64),
            sym("pack.ispf-pid.push-button", "electrical", List.of(), 64, 64),
            sym("pack.ispf-pid.centrifugal-pump", "process", List.of("running"), 64, 64)
    );

    private static SymbolRef sym(
            String id,
            String category,
            List<String> bindings,
            int defaultWidth,
            int defaultHeight
    ) {
        return new SymbolRef(id, category, bindings, defaultWidth, defaultHeight);
    }
}
