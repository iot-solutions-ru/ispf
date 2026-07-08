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
 * BL-184: external certified partner directory stub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerProgramPartnersApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsThreeExternalPartners() throws Exception {
        mockMvc.perform(get("/api/v1/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.partners[0].id").value("acme-integrators"))
                .andExpect(jsonPath("$.partners[1].id").value("nordic-automation"))
                .andExpect(jsonPath("$.partners[2].id").value("pacific-ot"))
                .andExpect(jsonPath("$.partners[0].marketplaceUrl").exists())
                .andExpect(jsonPath("$.portalUrl").exists());
    }
}
