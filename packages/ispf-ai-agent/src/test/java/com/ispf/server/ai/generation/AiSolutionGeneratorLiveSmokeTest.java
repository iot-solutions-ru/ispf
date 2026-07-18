package com.ispf.server.ai.generation;

import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * BL-180: live plant description → tree + dashboard + alert when {@code ISPF_LLM_SMOKE=true}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ISPF_LLM_SMOKE", matches = "true")
class AiSolutionGeneratorLiveSmokeTest {

    private static final String PROMPT = """
            Describe a building HVAC plant with one AHU and zone comfort monitoring. \
            Need an overview dashboard and a high-status alert on the hub.
            """;

    @Autowired
    private AiSolutionGeneratorService solutionGeneratorService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void llmSmokeProperties(DynamicPropertyRegistry registry) {
        registry.add("ispf.ai.enabled", () -> "true");
        registry.add("ispf.ai.provider", () -> env("ISPF_AI_PROVIDER", "openai-compatible"));
        registry.add("ispf.ai.base-url", () -> env("ISPF_AI_BASE_URL", "http://127.0.0.1:8000/v1"));
        registry.add("ispf.ai.model", () -> env("ISPF_AI_MODEL", "gpt-4o-mini"));
        registry.add("ispf.ai.timeout-seconds", () -> env("ISPF_AI_TIMEOUT_SECONDS", "120"));
        String apiKey = firstNonBlank(System.getenv("ISPF_AI_API_KEY"), System.getenv("OPENAI_API_KEY"));
        if (apiKey != null && !apiKey.isBlank()) {
            registry.add("ispf.ai.api-key", () -> apiKey);
        }
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void plantDescriptionAppliesTreeDashboardAndAlert() throws Exception {
        Map<String, Object> result = solutionGeneratorService.generate(PROMPT, true, "admin");
        assertEquals("live", result.get("mode"), () -> "unexpected result: " + result);
        assertEquals("primitives", result.get("composition"), () -> "unexpected result: " + result);
        assertNotNull(result.get("appId"));
        assertNotNull(result.get("hubPath"));
        assertNotNull(result.get("alertPath"));
        assertNotNull(result.get("dashboardPath"));

        String appId = String.valueOf(result.get("appId"));
        String hubPath = String.valueOf(result.get("hubPath"));
        String alertPath = String.valueOf(result.get("alertPath"));
        String dashboardPath = String.valueOf(result.get("dashboardPath"));

        assertTrue(objectManager.tree().findByPath(hubPath).isPresent(), "hub missing: " + hubPath);
        assertTrue(
                objectManager.require(hubPath).getVariable("status").isPresent(),
                "status variable missing on hub"
        );
        assertTrue(objectManager.tree().findByPath(alertPath).isPresent(), "alert missing: " + alertPath);
        assertTrue(
                objectManager.tree().findByPath(dashboardPath).isPresent(),
                "dashboard missing: " + dashboardPath
        );

        var ui = mockMvc.perform(get("/api/v1/operator-apps/" + appId + "/ui")).andReturn();
        assertEquals(200, ui.getResponse().getStatus(), ui.getResponse().getContentAsString());
        assertTrue(alertPath.startsWith(AutomationTreeService.ALERT_RULES_ROOT));
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
