package com.ispf.server.federation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@Tag("federation")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class FederationSecretsKeyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configureSecretsKeyViaApi() throws Exception {
        String token = loginAdmin();

        mockMvc.perform(get("/api/v1/federation/secrets-key/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.source").value("YAML"));

        mockMvc.perform(post("/api/v1/federation/secrets-key")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "secretsKey": "another-test-secrets-key-value" }
                                """))
                .andExpect(status().isConflict());
    }

    private String loginAdmin() throws Exception {
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
