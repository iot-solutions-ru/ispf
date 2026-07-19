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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class TenantIsolationWriteTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tenantOperatorUsesVirtualPlatformAndCannotWriteOtherTenant() throws Exception {
        String adminToken = login("admin", "admin");
        String suffix = Long.toHexString(System.nanoTime()).substring(0, 6);
        String tenantId = "write-gate-" + suffix;

        String tenantAdmin = tenantId + "-admin";
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "displayName": "Write Gate",
                                  "enabled": true,
                                  "adminPassword": "write-secret"
                                }
                                """.formatted(tenantId)))
                .andExpect(status().isOk());

        String tenantToken = login(tenantAdmin, "write-secret");

        // Sole-tenant virtual root: root.platform.* is the caller's world.
        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "name": "allowed-device",
                                  "type": "DEVICE",
                                  "displayName": "Allowed"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.tenant.other.platform.devices",
                                  "name": "blocked-device",
                                  "type": "DEVICE",
                                  "displayName": "Blocked"
                                }
                                """))
                .andExpect(status().isForbidden());
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
