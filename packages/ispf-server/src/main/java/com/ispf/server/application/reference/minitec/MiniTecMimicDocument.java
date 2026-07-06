package com.ispf.server.application.reference.minitec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

/**
 * SCADA mimic document JSON for mini-TEC diagrams.
 */
public final class MiniTecMimicDocument {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String DIAGRAM_JSON = load("/bootstrap/mini-tec-mimic.json");
    public static final String ZONE_GAS_JSON = withSharedCustomSymbols(load("/bootstrap/mini-tec-zone-gas.json"));
    public static final String ZONE_ELECTRICAL_JSON = withSharedCustomSymbols(load("/bootstrap/mini-tec-zone-electrical.json"));

    private static String load(String resource) {
        try (InputStream in = MiniTecMimicDocument.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resource, e);
        }
    }

    /** Zone mimics reference {@code custom:lib-*} symbols defined on the main SLD document. */
    private static String withSharedCustomSymbols(String diagramJson) {
        try {
            ObjectNode doc = (ObjectNode) MAPPER.readTree(diagramJson);
            JsonNode shared = MAPPER.readTree(DIAGRAM_JSON).path("customSymbols");
            if (!shared.isArray()) {
                return diagramJson;
            }
            LinkedHashMap<String, JsonNode> byId = new LinkedHashMap<>();
            for (JsonNode sym : doc.path("customSymbols")) {
                if (sym.hasNonNull("id")) {
                    byId.put(sym.get("id").asText(), sym);
                }
            }
            for (JsonNode sym : shared) {
                if (sym.hasNonNull("id")) {
                    byId.putIfAbsent(sym.get("id").asText(), sym);
                }
            }
            ArrayNode merged = MAPPER.createArrayNode();
            byId.values().forEach(merged::add);
            doc.set("customSymbols", merged);
            return MAPPER.writeValueAsString(doc);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to merge mini-TEC mimic customSymbols", e);
        }
    }

    private MiniTecMimicDocument() {
    }
}
