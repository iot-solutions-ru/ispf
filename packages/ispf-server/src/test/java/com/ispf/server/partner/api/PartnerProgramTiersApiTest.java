package com.ispf.server.partner.api;

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
 * BL-184: partner program tier listing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerProgramTiersApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsBronzeSilverGoldTiers() throws Exception {
        mockMvc.perform(get("/api/v1/partners/tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.tiers[0].id").value("bronze"))
                .andExpect(jsonPath("$.tiers[1].id").value("silver"))
                .andExpect(jsonPath("$.tiers[2].id").value("gold"));
    }
}
