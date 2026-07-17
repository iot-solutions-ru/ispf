package com.ispf.server.application.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-185: symbol marketplace listing + filesystem install + scada pack API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
class MarketplaceSymbolListingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listSymbolPacksReturnsBundledAndLocal() throws Exception {
        mockMvc.perform(get("/api/v1/marketplace/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.count").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.listings[0].slug").value("ispf-pid-v1"))
                .andExpect(jsonPath("$.listings[0].artifactKind").value("symbol-pack"))
                .andExpect(jsonPath("$.listings[0].packId").value("ispf-pid-v1"));
    }

    @Test
    void installsHvacPackThenScadaApiListsIt() throws Exception {
        mockMvc.perform(post("/api/v1/marketplace/symbols/hvac-equipment-v1/install"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.action").value("install"))
                .andExpect(jsonPath("$.packId").value("hvac-equipment-v1"))
                .andExpect(jsonPath("$.source").value("local-marketplace"));

        mockMvc.perform(get("/api/v1/scada/symbol-packs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.count").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/scada/symbol-packs/hvac-equipment-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packId").value("hvac-equipment-v1"))
                .andExpect(jsonPath("$.categories[0].symbols").isArray())
                .andExpect(jsonPath("$.categories[0].symbols[0].id").value("pack.hvac.ahu"));
    }

    @Test
    void rejectsUnknownSymbolPack() throws Exception {
        mockMvc.perform(post("/api/v1/marketplace/symbols/unknown-pack/install"))
                .andExpect(status().isBadRequest());
    }
}
