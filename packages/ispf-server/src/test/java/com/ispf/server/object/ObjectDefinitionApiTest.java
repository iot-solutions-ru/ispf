package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObjectDefinitionApiTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsVariableAndBindingRule() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "derivedTemp",
                                  "schema": {
                                    "name": "derivedTemp",
                                    "fields": [
                                      { "name": "value", "type": "DOUBLE" },
                                      { "name": "unit", "type": "STRING" }
                                    ]
                                  },
                                  "readable": true,
                                  "writable": false,
                                  "historyEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("derivedTemp"));

        saveBindingRule("""
                {
                  "id": "derived-temp",
                  "name": "derivedTemp",
                  "enabled": true,
                  "order": 0,
                  "activators": {
                    "onStartup": false,
                    "onVariableChange": [{ "objectPath": "self", "variableName": "*" }],
                    "onEvent": null,
                    "periodicMs": 0
                  },
                  "condition": "",
                  "expression": "self.temperature.value + 1.0",
                  "target": { "variableName": "derivedTemp", "field": "value" }
                }
                """);

        mockMvc.perform(delete("/api/v1/objects/by-path/binding-rules/derived-temp")
                        .param("path", DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .param("name", "derivedTemp"))
                .andExpect(status().isNoContent());
    }

    @Test
    void savesHistorianBindingRuleWithLowercaseKind() throws Exception {
        saveBindingRule("""
                {
                  "id": "hist-test",
                  "name": "hist-test",
                  "enabled": true,
                  "order": 0,
                  "kind": "historian",
                  "windowBucket": "1h",
                  "activators": {
                    "onStartup": false,
                    "onVariableChange": [{
                      "objectPath": "root.platform.devices.analytics-demo.sensor-a",
                      "variableName": "temperature"
                    }],
                    "onEvent": null,
                    "periodicMs": 60000
                  },
                  "condition": "",
                  "expression": "avg(root.platform.devices.analytics-demo.sensor-a/temperature, 1h)",
                  "target": { "kind": "variable", "variableName": "test", "field": "value" }
                }
                """);

        mockMvc.perform(get("/api/v1/objects/by-path/binding-rules").param("path", DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='hist-test' && @.kind=='historian')]").exists());

        mockMvc.perform(delete("/api/v1/objects/by-path/binding-rules/hist-test")
                        .param("path", DEVICE))
                .andExpect(status().isOk());
    }

    @Test
    void managesFunctionsAndEvents() throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/functions")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "testFn",
                                  "description": "Test",
                                  "inputSchema": { "name": "in", "fields": [] },
                                  "outputSchema": { "name": "out", "fields": [] }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("testFn"));

        mockMvc.perform(delete("/api/v1/objects/by-path/functions")
                        .param("path", DEVICE)
                        .param("name", "testFn"))
                .andExpect(status().isNoContent());
    }

    /**
     * PUT replaces the whole list. Keep existing rules (including blueprint-owned ones) and upsert the new rule.
     */
    private void saveBindingRule(String ruleJson) throws Exception {
        MvcResult listed = mockMvc.perform(get("/api/v1/objects/by-path/binding-rules").param("path", DEVICE))
                .andExpect(status().isOk())
                .andReturn();
        ArrayNode rules = (ArrayNode) objectMapper.readTree(listed.getResponse().getContentAsString());
        ObjectNode rule = (ObjectNode) objectMapper.readTree(ruleJson);
        String ruleId = rule.path("id").asText();
        ArrayNode merged = objectMapper.createArrayNode();
        for (JsonNode existing : rules) {
            if (!ruleId.equals(existing.path("id").asText())) {
                merged.add(existing);
            }
        }
        merged.add(rule);

        mockMvc.perform(put("/api/v1/objects/by-path/binding-rules")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(merged)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + ruleId + "')]").exists());
    }
}
