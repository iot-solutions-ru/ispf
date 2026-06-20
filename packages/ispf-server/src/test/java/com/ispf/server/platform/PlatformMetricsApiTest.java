package com.ispf.server.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class PlatformMetricsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanReadPlatformMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/platform/metrics")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[?(@.id == 'variableHistory')].values.sampleCount").exists())
                .andExpect(jsonPath("$.sections[?(@.id == 'runtime')].values.uptimeMs").exists())
                .andExpect(jsonPath("$.sections[?(@.id == 'database')].values").exists())
                .andExpect(jsonPath("$.sections[?(@.id == 'drivers')].values.activeDrivers").exists())
                .andExpect(jsonPath("$.sections[?(@.id == 'security')].values.platformUsers").exists());
    }

    @Test
    void operatorCannotReadPlatformMetrics() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "operator", "password": "operator" }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String token = login.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/platform/metrics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String adminToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
