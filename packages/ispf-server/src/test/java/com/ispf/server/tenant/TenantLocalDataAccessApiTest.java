package com.ispf.server.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant callers must not use the local/platform DB — external JDBC only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class TenantLocalDataAccessApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tenantAdminCannotUseInternalOrLocalJdbc_globalAdminInternalStillOk() throws Exception {
        String suffix = Long.toHexString(System.nanoTime()).substring(0, 6);
        String tenantId = "ds" + suffix;
        String tenantAdmin = tenantId + "-admin";

        String globalAdminToken = login("admin", "admin");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + globalAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "displayName": "DS Tenant",
                                  "enabled": true,
                                  "adminPassword": "tenant-secret"
                                }
                                """.formatted(tenantId)))
                .andExpect(status().isOk());

        String tenantToken = login(tenantAdmin, "tenant-secret");

        mockMvc.perform(post("/api/v1/data-sources")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "tenant-internal-%s",
                                  "displayName": "Blocked internal",
                                  "connectionMode": "internal",
                                  "schemaName": "app_tenant_blocked"
                                }
                                """.formatted(suffix)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.data-sources",
                                  "name": "remote-%s",
                                  "type": "DATA_SOURCE",
                                  "displayName": "Remote"
                                }
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.data-sources.remote-" + suffix));

        mockMvc.perform(put("/api/v1/data-sources/by-path")
                        .header("Authorization", "Bearer " + tenantToken)
                        .param("path", "root.platform.data-sources.remote-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Remote loopback",
                                  "connectionMode": "external",
                                  "jdbcUrl": "jdbc:postgresql://127.0.0.1:5432/tenant_db",
                                  "jdbcUsername": "u",
                                  "jdbcPassword": "p"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/data-sources/by-path/test-connection")
                        .header("Authorization", "Bearer " + tenantToken)
                        .param("path", "root.platform.data-sources.remote-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .param("packageId", "tenant-ds-admin-ok-" + suffix)
                        .header("Authorization", "Bearer " + globalAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "displayName": "Admin internal DS ok",
                                  "schemaName": "app_tenant_ds_admin_%s",
                                  "migrations": []
                                }
                                """.formatted(suffix)))
                .andExpect(status().isOk());

        String adminDsPath = "root.platform.data-sources.tenant-ds-admin-ok-" + suffix;
        mockMvc.perform(post("/api/v1/data-sources/by-path/test-connection")
                        .header("Authorization", "Bearer " + globalAdminToken)
                        .param("path", adminDsPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true));
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
