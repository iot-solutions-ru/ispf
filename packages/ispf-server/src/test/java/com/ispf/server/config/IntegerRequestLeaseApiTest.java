package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code spring.jackson.deserialization.accept-float-as-int=false} is active on the HTTP mapper.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegerRequestLeaseApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin-int-json", roles = "admin")
    void fractionalTtlMinutesIsRejectedBySpringMapper() throws Exception {
        String prefix = "root.platform.devices.int-json-" + System.nanoTime();
        mockMvc.perform(post("/api/v1/objects/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPrefix\":\"" + prefix + "\",\"ttlMinutes\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON request body"));

        mockMvc.perform(post("/api/v1/objects/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPrefix\":\"" + prefix + "\",\"ttlMinutes\":2.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON request body"));

        mockMvc.perform(post("/api/v1/objects/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPrefix\":\"" + prefix + "\",\"ttlMinutes\":1e1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON request body"));

        mockMvc.perform(post("/api/v1/objects/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPrefix\":\"" + prefix + "\",\"ttlMinutes\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pathPrefix").value(prefix));
    }
}
