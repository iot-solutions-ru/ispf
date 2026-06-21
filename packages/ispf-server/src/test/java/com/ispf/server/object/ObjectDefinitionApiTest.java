package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void createsVariableWithBindingAndUpdatesBinding() throws Exception {
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
                                  "bindingExpression": "self.temperature.value + 1.0",
                                  "historyEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("derivedTemp"))
                .andExpect(jsonPath("$.bindingExpression").value("self.temperature.value + 1.0"));

        mockMvc.perform(patch("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .param("name", "derivedTemp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bindingExpression": "self.temperature.value * 2.0" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingExpression").value("self.temperature.value * 2.0"));

        mockMvc.perform(delete("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .param("name", "derivedTemp"))
                .andExpect(status().isNoContent());
    }

    @Test
    void acceptsCounterRateBindingExpression() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "ifInOctetsRate",
                                  "schema": {
                                    "name": "ifInOctetsRate",
                                    "fields": [{ "name": "value", "type": "DOUBLE" }]
                                  },
                                  "readable": true,
                                  "writable": false,
                                  "bindingExpression": "counterRate(ifInOctets)",
                                  "historyEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingExpression").value("counterRate(ifInOctets)"));

        mockMvc.perform(delete("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .param("name", "ifInOctetsRate"))
                .andExpect(status().isNoContent());
    }

    @Test
    void managesFunctionsAndEvents() throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/functions")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "resetCounter",
                                  "description": "Reset demo counter",
                                  "inputSchema": {
                                    "name": "resetCounterInput",
                                    "fields": []
                                  },
                                  "outputSchema": {
                                    "name": "resetCounterOutput",
                                    "fields": [{ "name": "ok", "type": "BOOLEAN" }]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("resetCounter"));

        mockMvc.perform(put("/api/v1/objects/by-path/events")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "customAlert",
                                  "description": "Custom alert event",
                                  "payloadSchema": {
                                    "name": "customAlertPayload",
                                    "fields": [{ "name": "message", "type": "STRING" }]
                                  },
                                  "level": "WARNING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("customAlert"));

        mockMvc.perform(get("/api/v1/objects/by-path/editor")
                        .param("path", DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.functions[?(@.name=='resetCounter')]").exists())
                .andExpect(jsonPath("$.events[?(@.name=='customAlert')]").exists());

        mockMvc.perform(delete("/api/v1/objects/by-path/functions")
                        .param("path", DEVICE)
                        .param("name", "resetCounter"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/objects/by-path/events")
                        .param("path", DEVICE)
                        .param("name", "customAlert"))
                .andExpect(status().isNoContent());
    }

    @Test
    void clearsBindingWithEmptyString() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "boundFlag",
                                  "schema": {
                                    "name": "boundFlag",
                                    "fields": [{ "name": "value", "type": "BOOLEAN" }]
                                  },
                                  "readable": true,
                                  "writable": false,
                                  "bindingExpression": "true",
                                  "historyEnabled": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .param("name", "boundFlag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bindingExpression": "" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingExpression").value(nullValue()));

        mockMvc.perform(delete("/api/v1/objects/by-path/variables")
                        .param("path", DEVICE)
                        .param("name", "boundFlag"))
                .andExpect(status().isNoContent());
    }
}
