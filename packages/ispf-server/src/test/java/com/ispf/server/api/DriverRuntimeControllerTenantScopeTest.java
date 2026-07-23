package com.ispf.server.api;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant scope on driver runtime endpoints: tenant-admin is confined to their own branch,
 * global admin keeps platform-wide access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class DriverRuntimeControllerTenantScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tenantAdminCannotOperateDriversOutsideTenantBranch() throws Exception {
        String suffix = Long.toHexString(System.nanoTime()).substring(0, 6);
        String tenantId = "drv" + suffix;
        String globalAdminToken = login("admin", "admin");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + globalAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "displayName": "Driver Scope Co",
                                  "enabled": true,
                                  "adminPassword": "drv-secret"
                                }
                                """.formatted(tenantId)))
                .andExpect(status().isOk());

        String tenantAdminToken = login(tenantId + "-admin", "drv-secret");
        String foreignPath = "root.tenant.someone-else.platform.devices.pump";

        mockMvc.perform(get("/api/v1/drivers/runtime/status")
                        .param("devicePath", foreignPath)
                        .header("Authorization", "Bearer " + tenantAdminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", foreignPath)
                        .header("Authorization", "Bearer " + tenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // Virtual root.platform.* maps into the own tenant branch: scope passes, no binding yet.
        mockMvc.perform(get("/api/v1/drivers/runtime/status")
                        .param("devicePath", "root.platform.devices.no-such-device")
                        .header("Authorization", "Bearer " + tenantAdminToken))
                .andExpect(status().isBadRequest());

        // Global admin is unscoped: same virtual path passes scope, fails only on missing binding.
        mockMvc.perform(get("/api/v1/drivers/runtime/status")
                        .param("devicePath", "root.platform.devices.no-such-device")
                        .header("Authorization", "Bearer " + globalAdminToken))
                .andExpect(status().isBadRequest());
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
