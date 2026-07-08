package com.ispf.server.application.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-185: symbol marketplace install flow stub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceSymbolInstallApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsSymbolPacksThenInstallsFreePack() throws Exception {
        mockMvc.perform(get("/api/v1/marketplace/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.listings[0].slug").value("ispf-pid-v1"));

        mockMvc.perform(post("/api/v1/marketplace/symbols/ispf-pid-v1/install"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.action").value("install"))
                .andExpect(jsonPath("$.packId").value("ispf-pid-v1"))
                .andExpect(jsonPath("$.installationId").value(startsWith("symbol-install-")));
    }

    @Test
    void rejectsUnknownSymbolPack() throws Exception {
        mockMvc.perform(post("/api/v1/marketplace/symbols/unknown-pack/install"))
                .andExpect(status().isBadRequest());
    }
}
