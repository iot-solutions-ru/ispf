package com.ispf.server.partner.api;

import com.ispf.server.persistence.PartnerEnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-184: partner program enrollment persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerProgramEnrollApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerEnrollmentRepository partnerEnrollmentRepository;

    @Test
    void acceptsEnrollmentAndPersistsApplication() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/partners/enroll")
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
                .andExpect(jsonPath("$.source").value("db"))
                .andExpect(jsonPath("$.tierId").value("silver"))
                .andExpect(jsonPath("$.applicationId").value(startsWith("partner-app-")))
                .andExpect(jsonPath("$.companyName").value("Acme Integrators"))
                .andExpect(jsonPath("$.portalUrl").exists())
                .andReturn();

        String applicationId = result.getResponse().getContentAsString()
                .replaceAll("(?s).*\"applicationId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(partnerEnrollmentRepository.findById(applicationId)).isPresent();
        assertThat(partnerEnrollmentRepository.findById(applicationId).orElseThrow().getTierId())
                .isEqualTo("silver");
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
                .andExpect(jsonPath("$.source").value("db"))
                .andExpect(jsonPath("$.tierId").value("bronze"));
    }
}
