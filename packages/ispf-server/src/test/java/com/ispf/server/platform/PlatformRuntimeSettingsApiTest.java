package com.ispf.server.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class PlatformRuntimeSettingsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanListRuntimeSettings() throws Exception {
        mockMvc.perform(get("/api/v1/platform/runtime-settings")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.sections[*].id", hasItem("database")))
                .andExpect(jsonPath("$.sections[?(@.id=='database')].settings[*].id", hasItem("event-journal.store")))
                .andExpect(jsonPath("$.sections[?(@.id=='database')].settings[*].id", hasItem("variable-history.store")))
                .andExpect(jsonPath("$.sections[?(@.id=='database')].settings[*].id", hasItem("event-journal.clickhouse.url")))
                .andExpect(jsonPath("$.sections[*].settings[*].id", hasItem("mcp.enabled")));
    }

    @Test
    void adminCanPatchHotReloadableSetting() throws Exception {
        mockMvc.perform(patch("/api/v1/platform/runtime-settings")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"values":{"object-change.elastic-scale-up-threshold":"60"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedLive[0]").value("object-change.elastic-scale-up-threshold"));
    }

    @Test
    void adminCanPatchAiBaseUrlAndReadItBack() throws Exception {
        mockMvc.perform(patch("/api/v1/platform/runtime-settings")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"values":{"ai.provider":"openai-compatible","ai.base-url":"https://api.deepseek.com/v1"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restartRequired").value(true));

        mockMvc.perform(get("/api/v1/platform/runtime-settings")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[*].settings[?(@.id=='ai.base-url')].value", hasItem("https://api.deepseek.com/v1")))
                .andExpect(jsonPath("$.sections[*].settings[?(@.id=='ai.provider')].value", hasItem("openai-compatible")));
    }

    @Test
    void adminRestartIsDisabledInTestProfile() throws Exception {
        mockMvc.perform(post("/api/v1/platform/runtime-settings/restart")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.mode").value("disabled"));
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
