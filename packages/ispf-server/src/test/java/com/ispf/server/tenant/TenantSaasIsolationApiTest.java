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
class TenantSaasIsolationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tenantAdminOwnsBranchAndCannotSeeOtherTenantsOrGlobals() throws Exception {
        String suffix = Long.toHexString(System.nanoTime()).substring(0, 6);
        String acme = "acme" + suffix;
        String beta = "beta" + suffix;
        String acmeAdmin = acme + "-admin";
        String betaAdmin = beta + "-admin";
        String acmeOp = "acme-op-" + suffix;

        String globalAdminToken = login("admin", "admin");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + globalAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "displayName": "Acme",
                                  "enabled": true,
                                  "adminPassword": "acme-secret"
                                }
                                """.formatted(acme)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminUsername").value(acmeAdmin))
                .andExpect(jsonPath("$.platformPath").value("root.tenant." + acme + ".platform"));

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + globalAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "%s",
                                  "displayName": "Beta",
                                  "enabled": true,
                                  "adminPassword": "beta-secret"
                                }
                                """.formatted(beta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminUsername").value(betaAdmin));

        String acmeAdminToken = login(acmeAdmin, "acme-secret");

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + acmeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.tenant.%s.platform.devices",
                                  "name": "pump-a",
                                  "type": "DEVICE",
                                  "displayName": "Pump A"
                                }
                                """.formatted(acme)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + acmeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "name": "blocked-global",
                                  "type": "DEVICE",
                                  "displayName": "Blocked"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + acmeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.tenant.%s.platform.devices",
                                  "name": "blocked-cross",
                                  "type": "DEVICE",
                                  "displayName": "Blocked"
                                }
                                """.formatted(beta)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/security/users")
                        .header("Authorization", "Bearer " + acmeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "displayName": "Acme Operator",
                                  "password": "op-secret",
                                  "roles": ["operator"]
                                }
                                """.formatted(acmeOp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(acme))
                .andExpect(jsonPath("$.roles", hasItem("operator")));

        String acmeOpToken = login(acmeOp, "op-secret");

        mockMvc.perform(get("/api/v1/objects")
                        .header("Authorization", "Bearer " + acmeOpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.tenant." + acme + ".platform.devices.pump-a")))
                .andExpect(jsonPath("$[*].path", not(hasItem("root.tenant." + beta + ".platform.devices"))));

        String betaAdminToken = login(betaAdmin, "beta-secret");

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + betaAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.tenant.%s.platform.devices",
                                  "name": "pump-b",
                                  "type": "DEVICE",
                                  "displayName": "Pump B"
                                }
                                """.formatted(beta)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects")
                        .header("Authorization", "Bearer " + acmeAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.tenant." + acme + ".platform.devices.pump-a")))
                .andExpect(jsonPath("$[*].path", not(hasItem("root.tenant." + beta + ".platform.devices.pump-b"))));

        mockMvc.perform(get("/api/v1/objects")
                        .header("Authorization", "Bearer " + betaAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("root.tenant." + beta + ".platform.devices.pump-b")))
                .andExpect(jsonPath("$[*].path", not(hasItem("root.tenant." + acme + ".platform.devices.pump-a"))));

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + acmeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "evil%s",
                                  "displayName": "Evil"
                                }
                                """.formatted(suffix)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/security/users")
                        .header("Authorization", "Bearer " + acmeAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem(acmeAdmin)))
                .andExpect(jsonPath("$[*].username", hasItem(acmeOp)))
                .andExpect(jsonPath("$[*].username", not(hasItem("admin"))));
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
