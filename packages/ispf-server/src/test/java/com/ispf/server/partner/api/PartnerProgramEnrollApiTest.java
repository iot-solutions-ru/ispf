package com.ispf.server.partner.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-184: partner program enrollment stub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerProgramEnrollApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsEnrollmentAndReturnsApplicationId() throws Exception {
        mockMvc.perform(post("/api/v1/partners/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Acme Integrators",
                                  "contactEmail": "ops@acme.example",
                                  "tierId": "silver",
                                  "verticals": ["scada", "hvac"],
                                  "regions": ["EMEA"]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.tierId").value("silver"))
                .andExpect(jsonPath("$.applicationId").value(startsWith("partner-app-")))
                .andExpect(jsonPath("$.companyName").value("Acme Integrators"))
                .andExpect(jsonPath("$.portalUrl").exists());
    }

    @Test
    void defaultsToBronzeWhenTierUnknown() throws Exception {
        mockMvc.perform(post("/api/v1/partners/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Lab Partner",
                                  "tierId": "platinum"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tierId").value("bronze"));
    }
}
