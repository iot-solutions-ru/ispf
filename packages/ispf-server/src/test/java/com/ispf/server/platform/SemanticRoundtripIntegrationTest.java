package com.ispf.server.platform;

import com.ispf.server.bootstrap.HaystackBlueprintBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-105 (S23-03): Haystack export, query, and Brick export stay consistent for tagged demo devices.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SemanticRoundtripIntegrationTest {

    private static final String DEMO_PATH = HaystackBlueprintBootstrap.DEMO_DEVICE_PATH;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void semanticRoundtripForLabDemoDevice() throws Exception {
        JsonNode haystackExport = readJson(mockMvc.perform(get("/api/v1/platform/haystack/export")
                        .param("rootPath", DEMO_PATH)
                        .param("includePoints", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formatVersion").value(1))
                .andExpect(jsonPath("$.rootPath").value(DEMO_PATH))
                .andExpect(jsonPath("$.rowCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn());

        JsonNode brickExport = readJson(mockMvc.perform(get("/api/v1/platform/brick/export")
                        .param("rootPath", DEMO_PATH)
                        .param("format", "jsonld")
                        .param("includePoints", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formatVersion").value(1))
                .andExpect(jsonPath("$.format").value("jsonld"))
                .andExpect(jsonPath("$.entityCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn());

        Map<String, JsonNode> graphByPath = indexGraphByIspfPath(brickExport.get("@graph"));

        for (JsonNode row : haystackExport.get("rows")) {
            String entityKind = row.path("entityKind").asText();
            JsonNode tags = row.path("tags");
            if (tags.isEmpty()) {
                continue;
            }

            if ("equip".equals(entityKind)) {
                String path = row.path("path").asText();
                assertQueryContains("equip and lab", "equip", path, null);

                JsonNode brickNode = graphByPath.get(path);
                assertNotNull(brickNode, "Brick graph must include equip with tags: " + path);
                assertEquals(BrickExportService.entityIri(path), brickNode.path("@id").asText());
                assertEquals("brick:Sensor", brickNode.path("@type").asText());
            }

            if ("point".equals(entityKind)) {
                String devicePath = row.path("path").asText();
                String variableName = row.path("variableName").asText();
                String pointPath = devicePath + "." + variableName;

                if (tags.path("temp").asBoolean(false)) {
                    assertQueryContains("point and temp", "point", devicePath, variableName);

                    JsonNode pointNode = graphByPath.get(pointPath);
                    assertNotNull(pointNode, "Brick graph must include temp point: " + pointPath);
                    assertEquals(
                            BrickExportService.entityIri(devicePath, variableName),
                            pointNode.path("@id").asText()
                    );
                    assertEquals("brick:Temperature_Sensor", pointNode.path("@type").asText());
                }
            }
        }

        JsonNode equipNode = graphByPath.get(DEMO_PATH);
        assertNotNull(equipNode);
        JsonNode hasPoint = equipNode.path("brick:hasPoint");
        assertTrue(hasPoint.isArray() && !hasPoint.isEmpty());
        assertTrue(hasPoint.toString().contains(BrickExportService.entityIri(DEMO_PATH, "sineWave")));
    }

    @Test
    void inferEndpointAlignsWithBrickExportForDemoDevice() throws Exception {
        JsonNode inferPayload = readJson(mockMvc.perform(get("/api/v1/platform/brick/infer")
                        .param("objectPath", DEMO_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectPath").value(DEMO_PATH))
                .andExpect(jsonPath("$.brickClassCompact").exists())
                .andReturn());

        assertFalse(inferPayload.path("brickClass").asText("").isBlank());
        assertTrue(inferPayload.path("tags").isArray());
        assertFalse(inferPayload.path("tags").isEmpty());
    }

    @Test
    void tagInferenceRoundtripsToBrickExportClass() throws Exception {
        mockMvc.perform(get("/api/v1/platform/brick/infer")
                        .param("tags", "equip,meter,energy")
                        .param("haystackKind", "equip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brickClassCompact").value("brick:Meter"));
    }

    private void assertQueryContains(
            String filter,
            String entityKind,
            String objectPath,
            String variableName
    ) throws Exception {
        MvcResult queryResult = mockMvc.perform(get("/api/v1/platform/haystack/query")
                        .param("filter", filter)
                        .param("rootPath", DEMO_PATH)
                        .param("entityKind", entityKind))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filter").value(filter))
                .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn();

        JsonNode matches = readJson(queryResult).path("matches");
        boolean found = false;
        for (JsonNode match : matches) {
            if (variableName == null) {
                if (objectPath.equals(match.path("objectPath").asText())) {
                    found = true;
                    break;
                }
            } else if (objectPath.equals(match.path("objectPath").asText())
                    && variableName.equals(match.path("variableName").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Query " + filter + " must return " + objectPath
                + (variableName != null ? "." + variableName : ""));
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static Map<String, JsonNode> indexGraphByIspfPath(JsonNode graph) {
        Map<String, JsonNode> byPath = new HashMap<>();
        if (graph == null || !graph.isArray()) {
            return byPath;
        }
        for (JsonNode node : graph) {
            byPath.put(node.path("ispf:path").asText(), node);
        }
        return byPath;
    }
}
