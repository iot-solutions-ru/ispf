package com.ispf.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsFormulasApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void crudSiteFormulaAndCatalogTierB() throws Exception {
        String createBody = """
                {
                  "id": "tank-fill-rate-test",
                  "displayName": "Tank fill rate",
                  "kind": "historian",
                  "expression": "rateOfChange({{levelPath}}, 1h) * {{tankArea}}",
                  "parameters": [
                    { "name": "levelPath", "type": "tagPath", "required": true },
                    { "name": "tankArea", "type": "number", "required": false, "defaultValue": "1" }
                  ],
                  "scope": "site",
                  "version": 1
                }
                """;

        mockMvc.perform(post("/api/v1/platform/analytics/formulas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("tank-fill-rate-test"))
                .andExpect(jsonPath("$.tier").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/v1/platform/analytics/formulas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem("tank-fill-rate-test")));

        mockMvc.perform(get("/api/v1/platform/analytics/catalog/tank-fill-rate-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("B"))
                .andExpect(jsonPath("$.pack").value("site"));

        mockMvc.perform(post("/api/v1/platform/analytics/formulas/tank-fill-rate-test/expand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": "site",
                                  "parameters": {
                                    "levelPath": "root.platform.devices.demo-sensor-01.temperature",
                                    "tankArea": "2"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expression")
                        .value("rateOfChange(root.platform.devices.demo-sensor-01.temperature, 1h) * 2"));

        mockMvc.perform(put("/api/v1/platform/analytics/formulas/tank-fill-rate-test?scope=site")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody.replace("Tank fill rate", "Tank fill rate v2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formula.displayName").value("Tank fill rate v2"))
                .andExpect(jsonPath("$.formula.version").value(2))
                .andExpect(jsonPath("$.reboundRules").exists());

        mockMvc.perform(delete("/api/v1/platform/analytics/formulas/tank-fill-rate-test?scope=site"))
                .andExpect(status().isOk());
    }
}
