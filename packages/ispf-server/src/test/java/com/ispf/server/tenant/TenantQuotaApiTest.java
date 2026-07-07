package com.ispf.server.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class TenantQuotaApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deviceQuotaBlocksCreateWhenExceeded() throws Exception {
        String adminToken = login("admin", "admin");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "quota-demo",
                                  "displayName": "Quota Demo",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/tenants/quota-demo/quotas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "maxDevices": 0, "maxObjects": 100 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDevices", is(0)));

        mockMvc.perform(put("/api/v1/tenants/quota-demo/users/operator")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.tenant.quota-demo.platform.devices",
                                  "name": "dev-01",
                                  "type": "DEVICE",
                                  "displayName": "Dev 01"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    private String login(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
