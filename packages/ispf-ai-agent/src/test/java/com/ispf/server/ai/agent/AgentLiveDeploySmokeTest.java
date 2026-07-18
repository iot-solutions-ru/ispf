package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * BL-177: live LLM deploy via {@code run_deploy_playbook} when {@code ISPF_LLM_SMOKE=true}.
 * Domain-agnostic acceptance: operator UI HTTP 200 + deploy tool in steps.
 * Optional {@code AGENT_LIVE_APP_ID} (default {@code mes-platform} — any context-pack example).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ISPF_LLM_SMOKE", matches = "true")
class AgentLiveDeploySmokeTest {

    private static final String APP_ID = env("AGENT_LIVE_APP_ID", "mes-platform");

    private static final String DEPLOY_PROMPT = """
            BL-177 live deploy for appId=%s. Call tool run_deploy_playbook with \
            {"appId":"%s"} exactly once. Do not invent SQL or objects. After the tool \
            returns status OK, type=finish with summary that the app is live.
            """.formatted(APP_ID, APP_ID);

    @Autowired
    private TreeFirstAgentService agentService;

    @Autowired
    private AgentSessionStore sessionStore;

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
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void deployViaRealLlmOperatorUiLive() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_admin"))
        );

        Map<String, Object> response = agentService.run(DEPLOY_PROMPT, "root", auth, "admin");
        List<Map<String, Object>> allSteps = new ArrayList<>(extractSteps(response));
        String sessionId = String.valueOf(response.get("sessionId"));
        int turnsLeft = 4;
        while (turnsLeft-- > 0) {
            final Map<String, Object> turnResponse = response;
            assertNotEquals(
                    AgentTurnStatus.ERROR,
                    turnResponse.get("status"),
                    () -> "agent turn failed: " + turnResponse.get("summary")
            );

            DeployProbe probe = probeOperatorUi();
            if (probe.operatorUiOk()) {
                final List<Map<String, Object>> stepsSnapshot = List.copyOf(allSteps);
                assertTrue(
                        stepsSnapshot.stream().anyMatch(AgentLiveDeploySmokeTest::isImportStep),
                        () -> "expected run_deploy_playbook/import tool in steps: " + toolNames(stepsSnapshot)
                );
                return;
            }

            if (sessionId == null || sessionId.isBlank() || "null".equals(sessionId)) {
                break;
            }
            final String resumeSessionId = sessionId;
            AgentSession live = sessionStore.require(resumeSessionId, "admin")
                    .orElseThrow(() -> new IllegalStateException("missing agent session " + resumeSessionId));
            String resume = """
                    Verification failed (operatorUiHttp=%d). Call run_deploy_playbook with \
                    {"appId":"%s"} now, then finish. Do not explore.
                    """.formatted(probe.operatorUiStatus(), APP_ID);
            response = agentService.runTurn(live, resume, auth, "admin");
            allSteps.addAll(extractSteps(response));
            sessionId = String.valueOf(response.get("sessionId"));
        }

        DeployProbe finalProbe = probeOperatorUi();
        final List<Map<String, Object>> finalSteps = List.copyOf(allSteps);
        assertTrue(
                finalProbe.operatorUiOk(),
                () -> "app %s not live after LLM turns. operatorUiHttp=%d steps=%s"
                        .formatted(APP_ID, finalProbe.operatorUiStatus(), toolNames(finalSteps))
        );
    }

    private DeployProbe probeOperatorUi() throws Exception {
        var ui = mockMvc.perform(get("/api/v1/operator-apps/" + APP_ID + "/ui")).andReturn();
        int uiStatus = ui.getResponse().getStatus();
        return new DeployProbe(uiStatus, uiStatus == 200);
    }

    private record DeployProbe(int operatorUiStatus, boolean operatorUiOk) {
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractSteps(Map<String, Object> response) {
        Object steps = response.get("steps");
        if (steps instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                }
            }
            return out;
        }
        return List.of();
    }

    private static boolean isImportStep(Map<String, Object> step) {
        Object name = step.get("tool");
        if (name == null) {
            name = step.get("name");
        }
        String tool = String.valueOf(name);
        return "import_package".equals(tool)
                || "deploy_step_import".equals(tool)
                || "run_deploy_playbook".equals(tool);
    }

    private static List<String> toolNames(List<Map<String, Object>> steps) {
        return steps.stream()
                .map(s -> String.valueOf(s.getOrDefault("tool", s.get("name"))))
                .toList();
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
