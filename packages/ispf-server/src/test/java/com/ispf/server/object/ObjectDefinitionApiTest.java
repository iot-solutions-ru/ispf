package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

        mockMvc.perform(put("/api/v1/objects/by-path/binding-rules")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{
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
                                }]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("derived-temp"));

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
        mockMvc.perform(put("/api/v1/objects/by-path/binding-rules")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{
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
                                }]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("hist-test"))
                .andExpect(jsonPath("$[0].kind").value("historian"));

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
}
