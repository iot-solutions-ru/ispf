package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * BL-177: live LLM deploy via {@code run_deploy_playbook} when {@code ISPF_LLM_SMOKE=true}.
 * Domain-agnostic acceptance: operator UI HTTP 200 + deploy tool in steps.
 * <p>
 * Default matrix: {@code mes-platform}, {@code building-hvac}, {@code platform-primitive}.
 * Override with {@code AGENT_LIVE_APP_ID} (single) or {@code AGENT_LIVE_APP_IDS} (comma-separated).
 * Skipped cleanly when {@code ISPF_LLM_SMOKE} is unset (no AI secrets required for compile/CI unit path).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ISPF_LLM_SMOKE", matches = "true")
class AgentLiveDeploySmokeTest {

    /** Default multi-app live smoke matrix (BL-177). */
    static final List<String> DEFAULT_LIVE_APP_IDS = List.of(
            "mes-platform",
            "building-hvac",
            "platform-primitive"
    );

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

    static Stream<String> liveAppIds() {
        String single = System.getenv("AGENT_LIVE_APP_ID");
        if (single != null && !single.isBlank()) {
            return Stream.of(single.trim());
        }
        String matrix = System.getenv("AGENT_LIVE_APP_IDS");
        if (matrix != null && !matrix.isBlank()) {
            return Arrays.stream(matrix.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank());
        }
        return DEFAULT_LIVE_APP_IDS.stream();
    }

    /**
     * Keeps the class discoverable for Gradle when {@code ISPF_LLM_SMOKE} is unset
     * (class-level disable + only {@code @ParameterizedTest} otherwise yields "No tests found").
     */
    @Test
    void defaultLiveAppMatrixConfigured() {
        assertEquals(3, DEFAULT_LIVE_APP_IDS.size());
        assertTrue(DEFAULT_LIVE_APP_IDS.contains("mes-platform"));
        assertTrue(DEFAULT_LIVE_APP_IDS.contains("building-hvac"));
        assertTrue(DEFAULT_LIVE_APP_IDS.contains("platform-primitive"));
    }

    @ParameterizedTest(name = "appId={0}")
    @MethodSource("liveAppIds")
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void deployViaRealLlmOperatorUiLive(String appId) throws Exception {
        String deployPrompt = """
                BL-177 live deploy for appId=%s. Call tool run_deploy_playbook with \
                {"appId":"%s"} exactly once. Do not invent SQL or objects. After the tool \
                returns status OK, type=finish with summary that the app is live.
                """.formatted(appId, appId);

        var auth = new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_admin"))
        );

        Map<String, Object> response = agentService.run(deployPrompt, "root", auth, "admin");
        List<Map<String, Object>> allSteps = new ArrayList<>(extractSteps(response));
        String sessionId = String.valueOf(response.get("sessionId"));
        int turnsLeft = 4;
        while (turnsLeft-- > 0) {
            final Map<String, Object> turnResponse = response;
            assertNotEquals(
                    AgentTurnStatus.ERROR,
                    turnResponse.get("status"),
                    () -> "agent turn failed for " + appId + ": " + turnResponse.get("summary")
            );

            DeployProbe probe = probeOperatorUi(appId);
            if (probe.operatorUiOk()) {
                final List<Map<String, Object>> stepsSnapshot = List.copyOf(allSteps);
                assertTrue(
                        stepsSnapshot.stream().anyMatch(AgentLiveDeploySmokeTest::isImportStep),
                        () -> "expected run_deploy_playbook/import tool in steps for " + appId
                                + ": " + toolNames(stepsSnapshot)
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
                    """.formatted(probe.operatorUiStatus(), appId);
            response = agentService.runTurn(live, resume, auth, "admin");
            allSteps.addAll(extractSteps(response));
            sessionId = String.valueOf(response.get("sessionId"));
        }

        DeployProbe finalProbe = probeOperatorUi(appId);
        final List<Map<String, Object>> finalSteps = List.copyOf(allSteps);
        assertTrue(
                finalProbe.operatorUiOk(),
                () -> "app %s not live after LLM turns. operatorUiHttp=%d steps=%s"
                        .formatted(appId, finalProbe.operatorUiStatus(), toolNames(finalSteps))
        );
    }

    private DeployProbe probeOperatorUi(String appId) throws Exception {
        var ui = mockMvc.perform(get("/api/v1/operator-apps/" + appId + "/ui")).andReturn();
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
