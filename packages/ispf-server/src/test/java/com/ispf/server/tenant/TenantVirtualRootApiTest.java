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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
class TenantVirtualRootApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tenantSeesSoleWorldAsRootPlatform() throws Exception {
        String suffix = Long.toHexString(System.nanoTime()).substring(0, 6);
        String acme = "sole" + suffix;
        String acmeAdmin = acme + "-admin";

        String globalAdminToken = login("admin", "admin");
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + globalAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "displayName": "Sole",
                                  "enabled": true,
                                  "adminPassword": "sole-secret"
                                }
                                """.formatted(acme)))
                .andExpect(status().isOk());

        String token = login(acmeAdmin, "sole-secret");

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "name": "pump-v",
                                  "type": "DEVICE",
                                  "displayName": "Pump V"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.devices.pump-v"));

        mockMvc.perform(get("/api/v1/objects")
                        .param("parent", "root")
                        .param("lite", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.platform")))
                .andExpect(jsonPath("$[*].path", not(hasItem("root.tenant"))))
                .andExpect(jsonPath("$[*].path", not(hasItem("root.tenant." + acme))));

        mockMvc.perform(get("/api/v1/objects")
                        .param("parent", "root.platform.devices")
                        .param("lite", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.devices.pump-v")));

        mockMvc.perform(get("/api/v1/objects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.platform.devices.pump-v")))
                .andExpect(jsonPath("$[*].path", not(hasItem("root.tenant." + acme + ".platform.devices.pump-v"))));

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", "root.tenant." + acme + ".platform.devices.pump-v")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("root.platform.devices.pump-v"));
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
