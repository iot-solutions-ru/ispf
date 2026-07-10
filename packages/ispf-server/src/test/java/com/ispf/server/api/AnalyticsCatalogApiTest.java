package com.ispf.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsCatalogApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listCatalogReturnsTierAEntries() throws Exception {
        mockMvc.perform(get("/api/v1/platform/analytics/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].displayName").isString())
                .andExpect(jsonPath("$[0].tier").value("A"))
                .andExpect(jsonPath("$[0].kinds").isArray())
                .andExpect(jsonPath("$[0].parameters").isArray());
    }

    @Test
    void getCatalogEntryByIdReturnsRollingAvg() throws Exception {
        mockMvc.perform(get("/api/v1/platform/analytics/catalog/rollingAvg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rollingAvg"))
                .andExpect(jsonPath("$.tier").value("A"))
                .andExpect(jsonPath("$.pack").value("core"))
                .andExpect(jsonPath("$.docAnchor").isString());
    }

    @Test
    void getCatalogIncludesCoreExtensionPackFunction() throws Exception {
        mockMvc.perform(get("/api/v1/platform/analytics/catalog/energyDelta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("energyDelta"))
                .andExpect(jsonPath("$.tier").value("C"))
                .andExpect(jsonPath("$.pack").value("core-ext"))
                .andExpect(jsonPath("$.kinds[?(@ == 'historian')]").exists());
    }

    @Test
    void getCatalogIncludesReactiveBinding() throws Exception {
        mockMvc.perform(get("/api/v1/platform/analytics/catalog/movingAvg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("movingAvg"))
                .andExpect(jsonPath("$.tier").value("A"))
                .andExpect(jsonPath("$.pack").value("core"))
                .andExpect(jsonPath("$.kinds[?(@ == 'reactive')]").exists());
    }

    @Test
    void validateReactiveCatalogExpressionReturnsValidForPlatformBinding() throws Exception {
        mockMvc.perform(post("/api/v1/platform/analytics/catalog/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind": "reactive",
                                  "expression": "movingAvg(temperature, 60)",
                                  "context": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test
    void validateReactiveCatalogExpressionReturnsInvalidForBrokenExpression() throws Exception {
        mockMvc.perform(post("/api/v1/platform/analytics/catalog/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind": "reactive",
                                  "expression": "movingAvg(",
                                  "context": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void validateCatalogExpressionReturnsValidForCorrectExpression() throws Exception {
        mockMvc.perform(post("/api/v1/platform/analytics/catalog/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind": "cel",
                                  "expression": "1 + 1",
                                  "context": {
                                    "objectPath": "root.platform"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test
    void validateCatalogExpressionReturnsInvalidForBrokenExpression() throws Exception {
        mockMvc.perform(post("/api/v1/platform/analytics/catalog/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind": "cel",
                                  "expression": "1 +",
                                  "context": {
                                    "objectPath": "root.platform"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors.length()").value(greaterThanOrEqualTo(1)));
    }
}
