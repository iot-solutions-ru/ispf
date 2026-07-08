package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-177: one-shot mes-platform deploy via real LLM when {@code ISPF_LLM_SMOKE=true}.
 * Skipped in default CI — opt-in only (nightly / manual with API key + LLM endpoint).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ISPF_LLM_SMOKE", matches = "true")
class AgentLiveDeploySmokeTest {

    private static final String APP_ID = "mes-platform";
    private static final String HUB_DEVICE = "root.platform.devices.mes-platform-hub";
    private static final String DEPLOY_PROMPT = """
            Deploy the mes-platform reference bundle (examples/mes-platform/bundle.json) \
            using AgentDeployPlaybook: validate, import, configure operator UI, verify BFF \
            mes_platform_listLines returns LINE-A01. Finish when operator app is live.
            """;

    @Autowired
    private TreeFirstAgentService agentService;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void llmSmokeProperties(DynamicPropertyRegistry registry) {
        registry.add("ispf.ai.enabled", () -> "true");
        registry.add("ispf.ai.provider", () -> env("ISPF_AI_PROVIDER", "openai-compatible"));
        registry.add("ispf.ai.base-url", () -> env("ISPF_AI_BASE_URL", "http://127.0.0.1:8000/v1"));
        registry.add("ispf.ai.model", () -> env("ISPF_AI_MODEL", "gpt-4o-mini"));
        registry.add("ispf.ai.agent-require-approval-for-mutate", () -> "false");
        registry.add("ispf.ai.timeout-seconds", () -> env("ISPF_AI_TIMEOUT_SECONDS", "600"));
        registry.add("ispf.ai.agent-max-steps", () -> env("ISPF_AI_AGENT_MAX_STEPS", "96"));
        String apiKey = firstNonBlank(System.getenv("ISPF_AI_API_KEY"), System.getenv("OPENAI_API_KEY"));
        if (apiKey != null && !apiKey.isBlank()) {
            registry.add("ispf.ai.api-key", () -> apiKey);
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void mesPlatformDeployViaRealLlm() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_admin"))
        );

        Map<String, Object> response = agentService.run(DEPLOY_PROMPT, "root", auth, "admin");

        assertNotEquals(
                AgentTurnStatus.ERROR,
                response.get("status"),
                () -> "agent turn failed: " + response.get("summary")
        );
        assertTrue(
                List.of(AgentTurnStatus.OK, AgentTurnStatus.AWAITING_CONTINUE).contains(response.get("status")),
                () -> "unexpected agent status: " + response.get("status")
        );

        mockMvc.perform(get("/api/v1/operator-apps/" + APP_ID + "/ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP_ID))
                .andExpect(jsonPath("$.dashboards[0].path").exists());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "mes_platform_listLines",
                                  "input": {
                                    "schema": { "name": "in", "fields": [] },
                                    "rows": [{}]
                                  }
                                }
                                """.formatted(HUB_DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(1)))
                .andExpect(jsonPath("$.result.rows[0].lineCode").value("LINE-A01"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) response.get("steps");
        assertTrue(steps != null && !steps.isEmpty(), "agent should record tool steps");
        assertEquals(AgentTurnStatus.OK, response.get("status"));
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
