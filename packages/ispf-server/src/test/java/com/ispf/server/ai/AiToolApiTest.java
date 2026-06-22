package com.ispf.server.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiToolApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextPackInfoIsAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/ai/tools/context-pack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextPackVersion").exists());
    }

    @Test
    void validateWarehouseBundle() throws Exception {
        String warehouse = new ClassPathResource("warehouse-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        String body = """
                {
                  "appId": "warehouse",
                  "manifest": %s
                }
                """.formatted(warehouse);

        mockMvc.perform(post("/api/v1/ai/tools/validate-bundle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.auditId").exists());
    }

    @Test
    void dryRunWarehouseBundleListsWouldApplySections() throws Exception {
        String warehouse = new ClassPathResource("warehouse-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        String body = """
                {
                  "appId": "warehouse",
                  "manifest": %s
                }
                """.formatted(warehouse);

        mockMvc.perform(post("/api/v1/ai/tools/dry-run-deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.wouldApply", hasItem("register")))
                .andExpect(jsonPath("$.wouldApply", hasItem("snapshot")));
    }

    @Test
    void listModelsWithNoopProviderReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/ai/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void agentToolsCatalogIsAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/ai/agent/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools[?(@.name == 'list_objects')]").exists())
                .andExpect(jsonPath("$.tools[?(@.name == 'create_object')]").exists())
                .andExpect(jsonPath("$.tools[?(@.name == 'delete_object')]").exists())
                .andExpect(jsonPath("$.tools[?(@.name == 'get_dashboard_layout')]").exists())
                .andExpect(jsonPath("$.tools[?(@.name == 'set_dashboard_layout')]").exists())
                .andExpect(jsonPath("$.tools[?(@.name == 'list_variables')]").exists())
                .andExpect(jsonPath("$.tools[?(@.name == 'configure_driver')]").exists());
    }

    @Test
    void agentSessionLifecycle() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/ai/agent/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootPath\":\"root\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.title").value("New chat"))
                .andReturn();

        String sessionId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.sessionId");

        mockMvc.perform(get("/api/v1/ai/agent/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.turns").isArray());

        mockMvc.perform(delete("/api/v1/ai/agent/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(get("/api/v1/ai/agent/sessions/" + sessionId))
                .andExpect(status().isNotFound());
    }
}
