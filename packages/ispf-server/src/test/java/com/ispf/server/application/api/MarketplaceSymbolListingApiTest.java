package com.ispf.server.application.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-185: symbol marketplace listing stub endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceSymbolListingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listSymbolPacksReturnsReferenceListing() throws Exception {
        mockMvc.perform(get("/api/v1/marketplace/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.source").value("stub"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.listings[0].slug").value("ispf-pid-v1"))
                .andExpect(jsonPath("$.listings[0].artifactKind").value("symbol-pack"))
                .andExpect(jsonPath("$.listings[0].packId").value("ispf-pid-v1"))
                .andExpect(jsonPath("$.listings[0].pricing").value("free"));
    }
}
